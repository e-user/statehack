;;;; This file is part of statehack.
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

(ns statehack.system.levels
  (:refer-clojure :exclude [load])
  (:require [statehack.entity :as entity]
            [statehack.entity.room :as room]
            [statehack.entity.serv-bot :as serv-bot]
            [statehack.entity.dart-gun :as dart-gun]
            [statehack.entity.token :as token]
            [statehack.entity.music :as music]
            [statehack.system.world :as world]
            [statehack.util :as util]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def rooms {"starting-lab" {:tiles {\X #(room/wall %&)
                                    \o #(room/door %& false)
                                    \O #(room/door %& true)
                                    \b #(serv-bot/serv-bot %&)
                                    \l (fn [& coords] [(dart-gun/dart-gun coords)
                                                       (token/token coords "Foo")])}
                            :music :medical}})

(defn extract-room [s tiles [x0 y0] floor]
  (let [token (fn [c] (get tiles c (constantly nil)))]
    (filter identity
            (flatten
             (for [[y row] (util/enumerate (str/split-lines s))
                   [x c] (util/enumerate row)]
               (let [[x1 y1] (util/matrix-add [x0 y0] [x y])
                     f (token c)]
                 (f x1 y1 floor)))))))

(defn load-room-resource [name]
  (or (io/resource (str "rooms/" name))
      (throw (ex-info (str "No such room resource found") {:name name}))))

(defn dimensions [level]
  (let [ps (map :position (entity/filter-capable [:position] level))]
    [(inc (apply max (map first ps)))
     (inc (apply max (map second ps)))]))

(defn load-room [name [x0 y0] floor]
  (let [{:keys [tiles music]} (rooms name)
        room (extract-room (slurp (load-room-resource name)) tiles [x0 y0] floor)
        [x1 y1] (util/matrix-add [x0 y0] (dimensions room))]
    (concat room
            (for [x (range x0 x1) y (range y0 y1)]
              (music/music music [x y floor])))))

(defn floor [game n]
  (let [es (world/capable-entities game :foundation)]
    (or (first (filter #(= (:floor %) n) es))
        (throw (ex-info (format "No floor for level %d found" n) {:level n})))))

(defn entity-floor [game e]
  {:pre [(:floor e)]}
  (floor game (:floor e)))

(defn in-bounds?
  "Is `[x y]` within the bounds of `foundation`?"
  [foundation [x y]]
  (let [[w h] foundation]
    (and (>= x 0) (>= y 0)
         (< x w) (< y h))))

(defn on-floor [floor es]
  (filter #(= (:floor %) floor) es))

(defn floor-entities [game floor]
  (on-floor floor (vals (world/entities game))))
