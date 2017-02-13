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

(ns statehack.system.memory
  "Memory system"
  (:require [clojure.set :as set]
            [statehack.component :as c]
            [statehack.entity :as entity]
            [statehack.system.levels :as levels]
            [statehack.system.sight :as sight]
            [statehack.system.world :as world]
            [statehack.util :as util]))

(defn entity-memory
  [e] (::c/memory e))

(defn update-memory [game e f & args]
  (apply world/update-entity-component game (::c/id e) ::c/memory f args))

(defn entity-level-memory
  ([e n] (get-in e [::c/memory ::c/levels n] {:entities {} :coordinates []}))
  ([e] (entity-level-memory e (::c/level e))))

(defn update-memory-level
  ([game e n f]
     (update-memory game e #(update-in % [::c/levels n] f)))
  ([game e f]
     (update-memory-level game e (::c/level (levels/entity-level game e)) f)))

(defn remember-view [game e]
  {:pre [(entity/capable? e ::c/sight ::c/memory ::c/level)]}
  (let [{:keys [::c/level]} (levels/entity-level game e)
        mask (sight/visible-mask game e)
        es (dissoc (util/index-by ::c/id (sight/visible-entities game level mask)) (::c/id e))
        forget (map ::c/id (filter #(mask (::c/position %))
                                (vals (:entities (entity-level-memory e)))))]
    (update-memory-level game e level
                         (fn [memory]
                           (-> memory
                               (update-in [:entities] #(apply dissoc % forget))
                               (update-in [:entities] merge es)
                               (update-in [:coordinates] set/union mask))))))

(defn system [game]
  (reduce remember-view game (world/capable-entities game ::c/memory ::c/sight ::c/level)))
