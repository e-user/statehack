(ns statehack.system.render
  (:require [lanterna.screen :as screen]
            [statehack.game.world :as world]
            [statehack.util :as util]
            [statehack.entity :as entity]
            [clojure.set :as set]))

(def tiles
  {:player "@"
   :nihil " "
   :empty "·"
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
   :open-door "▒"
   :swall "▢"
   :dialog-indicator "⌐"})

(def render-hierarchy (make-hierarchy))

(defn derive-render [tag parent]
  (alter-var-root #'render-hierarchy derive tag parent))

(defn render-dispatch [e game]
  (e :renderable))

(defmulti render #'render-dispatch :hierarchy #'render-hierarchy)
(defmethod render :player [& _] :player)

(defn- blit-dispatch [x y]
  [(render x) (render y)])

(defmulti blit #'blit-dispatch :hierarchy #'render-hierarchy)
(defmethod blit :default [x y] x)

(defn draw [canvas]
  (map #(map tiles %) canvas))

(defn canvas-blit [game canvas e]
  (let [{:keys [position]} e]
    (update-in canvas (reverse position) (constantly (render e game)))))

(defn rect [kind w h]
  (vec (repeat h (vec (repeat w kind)))))

(defn space [w h]
  (rect :empty w h))

(defn window [w h]
  {:pre [(pos? w) (pos? h)]}
  (apply concat
         [[(flatten [:tlcorner (repeat (- w 2) :hwall) :trcorner])]
          (repeat (- h 2) (flatten [:vwall (repeat (- w 2) :nihil) :vwall]))
          [(flatten [:blcorner (repeat (- w 2) :hwall) :brcorner])]]))

(defn move [x coll]
  (if (neg? x)
    (concat (repeat (Math/abs x) nil) coll)
    (drop x coll)))

(defn entity-canvas [entities]
  (map (partial reduce blit) (vals (group-by :position entities))))

(defmacro drawing [scr & body]
  `(do
     (screen/clear ~scr)
     ~@body
     (screen/redraw ~scr)))

(defn- draw-objects [game es]
  (let [{:keys [screen viewport]} game
        {:keys [foundation]} (world/current-world-state game)
        player (world/player-entity game)
        es (entity/filter-capable es :position)
        world (reduce (partial canvas-blit game) foundation
                      (entity-canvas es))
        [x y] viewport
        view (map (partial move x) (move y world))]
    (screen/put-sheet screen 0 0 (draw view))
    (apply screen/move-cursor screen (util/matrix-subtract (:position player) viewport))))

(defn- draw-interface [game es]
  (let [{:keys [screen]} game]
    (when-let [ms (seq (entity/filter-capable es :messages))]
      (let [{:keys [screen]} game
            {:keys [messages]} (first ms)
            [w h] (screen/get-size screen)
            window (window w 5)
            m (first messages)]
        (screen/put-sheet screen 0 (- h 5) (draw window))
        (screen/put-string screen 1 (- h 4) (tiles :dialog-indicator))
        (screen/put-string screen 2 (- h 4) m)
        (screen/move-cursor screen (+ (count m) 2) (- h 4))))))

(defn system [{:keys [screen] :as game}]
  (let [{:keys [entities]} (world/current-world-state game)
        es (entity/filter-capable (vals entities) :renderable)]
    (drawing screen
      (draw-objects game es)
      (draw-interface game es)))
  game)

; unused
(defn center [scr [x y]]
  (let [[w h] (screen/get-size scr)]
    [(- x (/ w 2)) (- y (/ h 2))]))

(defn into-bounds [canvas scr [x y]]
  (let [[sw sh] (screen/get-size scr)
        fw (count (first canvas))
        fh (count canvas)
        x (cond (or (< x 0)
                    (<= fw sw)) 0
                (>= (+ x sw) fw) (- fw sw)
                :default x)
        y (cond (or (< y 0)
                    (<= fh sh)) 0
                (>= (+ y sh) fh) (- fh sh)
                :default y)]
    [x y]))

(defn in-bounds? [canvas x y]
  (and (>= x 0) (>= y 0)
       (< x (count (first canvas))) (< y (count canvas))))

(defn filter-neighbors [e game f]
  (let [state (world/current-world-state game)
        [x y] (:position e)]
    (map #(world/entity-delta % e)
         (filter f (world/direct-neighbors state x y)))))

(doseq [d [:hdoor :vdoor]]
  (derive-render d :door))

(doseq [w [:tlcorner :trcorner :blcorner :brcorner :hwall :vwall
           :hdcross :hucross :vrcross :vlcross :cross :swall]]
  (derive-render w :wall))

(defmethod render :wall [wall game]
  (condp set/subset? (set (map #(world/entity-delta % wall) (filter :room (world/entity-neighbors wall game))))
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
    :swall))

(defmethod render :door [{:keys [open] :as door} game]
  (if open :open-door
      (condp set/subset? (set (map #(world/entity-delta % door) (filter :room (world/entity-neighbors door game))))
        #{[1 0] [-1 0]} :hdoor
        #{[0 1] [0 -1]} :vdoor
        :door)))

(defmethod blit [:player :door] [& xs]
  :player)

(defmethod blit [:door :player] [& xs]
  :player)
