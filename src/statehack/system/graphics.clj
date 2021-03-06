;;;; This file is part of statehack.
;;;;
;;;; Copyright © 2014-2017 Alexander Kahl <ak@sodosopa.io>
;;;;
;;;; statehack is free software: you can redistribute it and/or modify
;;;; it under the terms of the GNU General Public License as published by
;;;; the Free Software Foundation, either version 3 of the License, or
;;;; (at your option) any later version.
;;;;
;;;; statehack is distributed in the hope that it will be useful,
;;;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;;;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;;; GNU General Public License for more details.
;;;;
;;;; You should have received a copy of the GNU General Public License
;;;; along with statehack.  If not, see <http://www.gnu.org/licenses/>.

(ns statehack.system.graphics
  "Graphics facility"
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [halo.graphics :as graphics]
            [halo.screen :as screen]
            [statehack.algebra :as algebra]
            [statehack.component :as c]
            [statehack.component.menu :as cm]
            [statehack.entity :as entity]
            [statehack.system.door :as door]
            [statehack.system.input.receivers :as receivers]
            [statehack.system.inventory :as inventory]
            [statehack.system.levels :as levels]
            [statehack.system.memory :as memory]
            [statehack.system.messages :as messages]
            [statehack.system.name :as name]
            [statehack.system.position :as pos]
            [statehack.system.sight :as sight]
            [statehack.system.slots :as slots]
            [statehack.system.status :as status]
            [statehack.system.unique :as unique]
            [statehack.system.world :as world]
            [statehack.util :as util]))

(def colors
  {:black 16
   :red 1
   :green 2
   :yellow 3
   :blue 4
   :magenta 5
   :cyan 6
   :gray 7

   :lightblack 8
   :lightred 9
   :lightgreen 10
   :lightyellow 11
   :lightblue 12
   :lightmagenta 13
   :lightcyan 14
   :white 15})

(def render-hierarchy "Hierarchy for `render`" (make-hierarchy))

(defn render-dispatch
  "Dispatch for `render`"
  [game e offset]
  (:type e))

(defmulti render
  "Render layout element `e`"
  {:arglists '([game e offset])}
  #'render-dispatch :hierarchy #'render-hierarchy)

(def draw-hierarchy "Hierarchy for `draw`" (make-hierarchy))

(defn draw-dispatch
  "Dispatch for `draw`"
  [game view offset]
  (:binding view))

(defmulti draw
  "Draw `view`"
  {:arglists '([game view offset])}
  #'draw-dispatch :hierarchy #'draw-hierarchy)

(def blit-hierarchy "Hierarchy of blit precedence" (make-hierarchy))

(defn derive-blit
  "Derive for `blit-hierarchy`"
  [tag parent]
  (alter-var-root #'blit-hierarchy derive tag parent))

(def blit-order "Order of blit precedence" [:flying :standing :lying :background])

(defn blit
  "Evaluate to the entity with higher blit order"
  [e1 e2]
  (first (sort-by #(.indexOf blit-order (first (parents blit-hierarchy (::c/renderable %)))) [e1 e2])))

(def tiles
  "Mapping of tile keywords to characters"
  {:humanoid "@"
   :serv-bot "b"
   :corpse "%"
   :nihil " "
   :floor "·"
   :hwall "─"
   :vwall "│"
   :tlcorner "╭"
   :trcorner "╮"
   :blcorner "╰"
   :brcorner "╯"
   :hdcross "┬"
   :hucross "┴"
   :vrcross "├"
   :vlcross "┤"
   :cross "┼"
   :vdoor "║"
   :hdoor "═"
   :door "+"
   :swall "▢"
   :dialog-indicator "⌐"
   :weapon ")"
   :crate "☒"
   
   ; not in use
   :spell-up "⁀"
   :spell-down "‿"
   :spell-left "("
   :spell-right ")"
   :camera "⚷"
   :battery "🔋"})

(def tile-hierarchy "Hierarchy for `tile`" (make-hierarchy))

(defn derive-tile
  "Derive for `tile-hierarchy`"
  [tag parent]
  (alter-var-root #'tile-hierarchy derive tag parent))

(defn tile-dispatch
  "Dispatch for `tile`"
  [game e]
  (::c/renderable e))

(defmulti tile
  "Determine tile and color for rendering entity `e`"
  {:arglists '([game e])}
  #'tile-dispatch :hierarchy #'tile-hierarchy)

(defmethod tile :default [_ {:keys [::c/renderable ::c/color]}]
  {:tile renderable :color color})

(defn transform
  "Transform two-dimensional `canvas` from tile/color mapping to
  character/color vectors."
  [canvas]
  (mapv #(mapv (fn [{:keys [tile color background char]}]
                 [(if tile (tiles tile) (str char)) (colors color) (colors (or background :black))]) %)
        canvas))

(defn dye
  "Change the color of all tiles in `canvas` to `c`"
  [canvas c]
  (walk/postwalk #(if (map? %) (assoc % :color c) %) canvas))

(defn canvas-dimensions
  "The dimensions of `canvas`"
  [canvas]
  [(count (first canvas)) (count canvas)])

(defn canvas-update
  "Update tile at coordinates [x y] in `canvas` with `f`

  Ignore coordinates outside bounds."
  [canvas [x y] f]
  (let [[w h] (canvas-dimensions canvas)]
    (if (and (< -1 x w) (< -1 y h))
      (update-in canvas [y x] f)
      canvas)))

(defn rect
  "Generate monotonous rectancle of proportions `[w h]` with tiles of
  `kind` and color `c`."
  [kind c [w h]]
  (vec (repeat h (vec (repeat w {:tile kind :color c})))))

(defn space
  "Create space rectangle of proportions `[w h]` with color `c`."
  [c [w h]]
  (rect :nihil c [w h]))

(defn entity-canvas
  "Transform collection of `entities` into a canvas based on their positions."
  [entities]
  (map (partial reduce blit) (vals (group-by ::c/position entities))))

(defn entity-blit
  "Blit entity `e` onto `canvas`."
  [game canvas e]
  (canvas-update canvas (::c/position e) (constantly (tile game e))))

(defn- reduce-entities
  "Reduce entities onto `canvas`"
  [game canvas es]
  (reduce (partial entity-blit game) canvas (entity-canvas es)))

(defn- splice
  "Splice `source` into `target` at `offset` using blit operation `f`."
  [offset f target source]
  {:pre [(>= offset 0)]}
  (let [[pre target] (split-at offset target)
        [target post] (split-at (count source) target)]
    (vec (concat pre (map f target source) post))))

(defn- canvas-blit
  "Blit `source` canvas onto `target` at coordinates `[x0 y0]`."
  ([target source [x0 y0]]
   {:pre [(>= x0 0) (>= y0 0)]}
   (splice y0 (partial splice x0 #(or %2 %1))
           target source))
  ([target source]
   (canvas-blit target source [0 0])))

(defn- center-offset
  "Calculate the offset needed to center `[w1 h1]` into a region of
  width `w2` and height `h2`."
  [[w1 h1] [w2 h2]]
  [(int (max (/ (- w2 w1) 2) 0))
   (int (max (/ (- h2 h1) 2) 0))])

(defn- canvas-viewport
  "Calculate actual viewport"
  [canvas [w h] [x y]]
  (let [[cw ch] (canvas-dimensions canvas)
        [x0 y0] (map (partial max 0) (util/matrix-subtract [x y] [(/ w 2) (/ h 2)]))
        [x1 y1] [(min (+ x0 w) cw) (min (+ y0 h) ch)]]
    [[x0 y0] [x1 y1]]))

(defn- fit-in
  "Cut and/or center `canvas`

  Cut and center the area designated by `viewport` into an area of
  `dimensions`. `offset` leaves a margin on the blitting target."
  [canvas dimensions viewport offset]
  (let [[[x0 y0] [x1 y1]] viewport]
    (canvas-blit (space :black dimensions)
                 (subvec (mapv #(subvec % x0 x1) canvas) y0 y1)
                 offset)))

(defn mask-canvas
  "Apply `mask` to `canvas`, making everything outside the mask invisible"
  [canvas mask]
  (mapv (fn [y row]
          (mapv (fn [x tile]
                  (if (mask [x y])
                    tile
                    nil))
                util/enumeration row))
        util/enumeration canvas))

(defn memorized-world
  "Render the memorized world of `e`"
  [game e]
  (let [{:keys [::c/level ::c/foundation]} (levels/entity-level game e)
        {:keys [entities]} (memory/entity-level-memory e level)
        canvas (space :black foundation)
        es (vals entities)]
    (dye (reduce-entities game (space :black foundation) (vals entities)) :lightblack)))

(defn visible-world
  "Render the visible world of `e`"
  [game e]
  (let [canvas (space :gray (::c/foundation (levels/entity-level game e)))
        mask (sight/visible-mask game e)
        es (entity/filter-capable [::c/position ::c/renderable] (levels/level-entities game (::c/level e)))]
    (mask-canvas (reduce-entities game canvas es) mask)))

(defn put-canvas
  "Write `canvas` at coordinates `[x0 y0]`."
  ([graphics canvas x0 y0]
     (doseq [[y row] (util/enumerate canvas)
             [x [s c b]] (util/enumerate row)]
       (graphics/put graphics s (+ x x0) (+ y y0) :color c :background b)))
  ([graphics canvas]
   (put-canvas graphics canvas 0 0)))

(defn tilify-string
  "Make per-character tiles from string `s` using color `c`

  With optional background color `b`."
  ([s c]
   (tilify-string s c :black))
  ([s c b]
   (mapv (fn [chr] {:char chr :color c :background b}) s)))

(defn window
  "Produces a canvas frame"
  ([[w h] color {:keys [title]}]
   {:pre [(pos? w) (pos? h)]}
   (let [tile (fn [type] {:tile type :color color})
         s (if title (str "|" title "|") "")
         length (count s)]
     (apply concat
            [[(flatten [(tile :tlcorner) (tilify-string s :gray) (repeat (- w 2 length) (tile :hwall)) (tile :trcorner)])]
             (repeat (- h 2) (flatten [(tile :vwall) (repeat (- w 2) (tile :nihil)) (tile :vwall)]))
             [(flatten [(tile :blcorner) (repeat (- w 2) (tile :hwall)) (tile :brcorner)])]])))
  ([[w h] color] (window [w h] color {})))

#_(defn- draw-dialog
  "Draw the dialog portion of the interface using dialog-capable entity
  `e` at coordinates `[x y]`, proportions `[w h]`."
  [canvas e [x y] [w h]]
  (-> canvas
      (canvas-blit (window :gray [w h]) [x y])
      (canvas-update (util/matrix-add [x y] [1 1]) (constantly {:tile :dialog-indicator :color :gray}))
      (canvas-blit (tilify-string (messages/current e) :gray) (util/matrix-add [x y] [2 1]))))

(defmethod render :box [game {:keys [alignment children]} offset]
  (let [[step merge]
        (case alignment
          :horizontal [(fn [[w _]] [w 0])
                       (fn [acc view]
                         (if (empty? acc)
                           view
                           (map concat acc view)))]
          :vertical [(fn [[_ h]] [0 h]) concat])]
    (loop [acc [] [c & cs] children o offset]
      (if c (recur (merge acc (render game c o)) cs (util/matrix-add o (step (:dimensions c))))
          acc))))

(defmethod render :stack [game {:keys [children]} offset]
  (reduce canvas-blit (map #(render game % offset)
                           (reverse (filter :visible children)))))

(defmethod render :view [game {:keys [visible] :as view} offset]
  (if visible (draw game view offset) []))

;; World

(defmethod draw :world [{:keys [screen graphics viewport] :as game} {:keys [dimensions]} offset]
  (let [player (unique/unique-entity game :player)
        world (canvas-blit (memorized-world game player) (visible-world game player))
        [[x0 y0] [x1 y1]] (canvas-viewport world dimensions viewport)
        co (center-offset (canvas-dimensions world) dimensions)]
    (when-let [[x y] (::c/position (unique/unique-entity game :cursor))]
      (if (and (<= x0 x x1) (<= y0 y y1))
        (screen/move-cursor screen (util/matrix-add (util/matrix-subtract [x y] [x0 y0]) co offset))
        (screen/hide-cursor screen)))
    (fit-in world dimensions [[x0 y0] [x1 y1]] co)))

;; Messages

(defmethod draw :messages [game {:keys [dimensions]} _]
  (let [log (unique/unique-entity game :log)
        canvas (space :black dimensions)]
    (canvas-blit canvas (map #(tilify-string % :gray) (messages/recent log 5)))))

;; Status

(defmethod draw :status [game {:keys [dimensions]} _]
  (let [player (unique/unique-entity game :player)
        canvas (space :black dimensions)]
    (canvas-blit canvas [(tilify-string (status/text game player) :gray)])))

;; Menu

(defn- selectable-list
  "Make a selectable list from `items`"
  [screen [w h] items slotted active? index offset title]
  (when active? (screen/move-cursor screen (util/matrix-add [0 index] [1 2] offset)))
  (canvas-blit (window [w h] :gray {:title title})
               (map-indexed (fn [i {:keys [::c/id] :as item}]
                              (let [s (str (name/name item) (if (slotted id) " (slotted)" ""))]
                                (if (and active? (= index i))
                                  (tilify-string (format (str "%-" (- w 4) "s") s) :black :gray)
                                  (tilify-string s (if (slotted id) :red :gray) :black))))
                            items)
               [2 2]))

(defmethod draw :inventory [{:keys [screen] :as game} {:keys [dimensions]} offset]
  (let [menu (receivers/current game)
        {:keys [::cm/index ::cm/reference ::cm/frame]} (::cm/inventory menu)
        {:keys [::c/inventory] :as owner} (world/entity game reference)]
    (selectable-list screen dimensions (map (partial world/entity game) inventory) (slots/slotted-items owner) (= frame :inventory) index offset "Inventory")))

(defmethod draw :floor [{:keys [screen] :as game} {:keys [dimensions]} offset]
  (let [{:keys [::cm/index ::cm/reference ::cm/frame]} (::cm/inventory (receivers/current game))
        holder (world/entity game reference)
        pickups (inventory/available-pickups game holder)]
    (selectable-list screen dimensions pickups #{} (= frame :floor) index offset "Floor")))

;; Tile

(defmethod tile :humanoid [& _] {:tile :humanoid :color :gray})
(defmethod tile :serv-bot [& _] {:tile :serv-bot :color :lightred})
(defmethod tile :corpse [& _] {:tile :corpse :color :red})
(defmethod tile :camera [& _] {:tile :camera :color :gray})
(defmethod tile :battery [& _] {:tile :battery :color :gray})
(defmethod tile :crate [& _] {:tile :crate :color :gray})

(doseq [d [:hdoor :vdoor]]
  (derive-tile d :door))

(doseq [w [:tlcorner :trcorner :blcorner :brcorner :hwall :vwall
           :hdcross :hucross :vrcross :vlcross :cross :swall]]
  (derive-tile w :wall))

(defmethod tile :wall [game wall]
  {:tile (condp set/subset? (set (map #(pos/entity-delta % wall)
                                      (entity/filter-capable [::c/room] (pos/entity-neighbors game wall))))
           algebra/neighbor-deltas :nihil
           (set/difference algebra/neighbor-deltas #{[1 -1]}) :blcorner
           (set/difference algebra/neighbor-deltas #{[-1 -1]}) :brcorner
           (set/difference algebra/neighbor-deltas #{[1 1]}) :tlcorner
           (set/difference algebra/neighbor-deltas #{[-1 1]}) :trcorner
           
           (set/difference algebra/neighbor-deltas #{[-1 -1] [1 -1] [0 -1]}) :hwall
           (set/difference algebra/neighbor-deltas #{[1 1] [-1 1] [0 1]}) :hwall
           (set/difference algebra/neighbor-deltas #{[-1 0] [-1 -1] [-1 1]}) :vwall
           (set/difference algebra/neighbor-deltas #{[1 0] [1 1] [1 -1]}) :vwall

           #{[1 0] [-1 0] [0 1] [0 -1]} :cross
           #{[1 0] [-1 0] [0 1]} :hdcross
           #{[1 0] [-1 0] [0 -1]} :hucross
           #{[0 -1] [0 1] [1 0]} :vrcross
           #{[0 -1] [0 1] [-1 0]} :vlcross
           #{[1 0] [0 1]} :tlcorner
           #{[-1 0] [0 1]} :trcorner
           #{[0 -1] [1 0]} :blcorner
           #{[-1 0] [0 -1]} :brcorner
           #{[1 0]} :hwall #{[-1 0]} :hwall
           #{[0 -1]} :vwall #{[0 1]} :vwall
           :swall)
   :color (or (::c/color wall) :white)})

(defmethod tile :door [game door]
  {:tile (condp set/subset? (set (map #(pos/entity-delta % door) (entity/filter-capable [::c/room] (pos/entity-neighbors game door))))
           #{[1 0] [-1 0]} :hdoor
           #{[0 1] [0 -1]} :vdoor
           :door)
   :color (if (door/open? door) :lightblack :white)})

(derive-blit :humanoid :standing)
(derive-blit :floor :background)
(derive-blit :door :background)
(derive-blit :corpse :lying)
(derive-blit :weapon :lying)
(derive-blit :crate :standing)

(defmethod tile :weapon [game e]
  {:tile :weapon
   :color :white})

;; System

(defn system [{:keys [screen graphics layout] :as game}]
  (put-canvas graphics (transform (render game layout [0 0])))
  (screen/refresh screen)
  game)
