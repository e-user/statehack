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

(ns statehack.system.ai
  (:require [statehack.algebra :as algebra]
            [statehack.component :as c]
            [statehack.system.combat :as combat]
            [statehack.system.levels :as levels]
            [statehack.system.memory :as memory]
            [statehack.system.movement :as movement]
            [statehack.system.position :as pos]
            [statehack.system.sight :as sight]
            [statehack.system.skills :as skills]
            [statehack.system.transition :as transition]
            [statehack.system.unique :as unique]
            [statehack.system.world :as world]
            [statehack.util :as util]))

(def act-hierarchy (make-hierarchy))

(defn act-dispatch [game e] (::c/ai e))

(defmulti act #'act-dispatch :hierarchy #'act-hierarchy)

(defn known-player-location [game e]
  (let [visible-es (util/index-by ::c/id (sight/visible-entities game e))]
    [(visible-es (::c/id (unique/unique-entity game :player)))
     (:player (memory/entity-memory e))]))

(defn first-player-spot? [game e]
  (let [[sight memory] (known-player-location game e)]
    (and sight (not memory))))

(defn player-known? [game e]
  (let [[sight memory] (known-player-location game e)]
    (cond sight [:sight sight]
          memory [:memory memory])))

(defn player-nearby? [game e]
  (let [player-id (::c/id (unique/unique-entity game :player))
        es (util/index-by ::c/id (pos/entity-neighbors game e))]
    (es player-id)))

(defn move-random [game e]
  (let [moves (vals (movement/available-moves game e))]
    (if-let [move (rand-nth moves)]
      (move game)
      game)))

(defn path-to [game e target limit]
  (let [{:keys [::c/foundation]} (levels/entity-level game e)
        es (vals (dissoc (:entities (memory/entity-level-memory e)) (::c/id target)))
        os (set (map ::c/position es))]
    (next (algebra/a* (::c/position e) (::c/position target) foundation os limit))))

(defn move-towards [game e target limit]
  (let [{:keys [::c/foundation]} (levels/entity-level game e)
        es (vals (dissoc (:entities (memory/entity-level-memory e)) (::c/id target)))
        os (set (map ::c/position es))
        path (algebra/a* (::c/position e) (::c/position target) foundation os limit)]
    (if (> (count path) 1)
      (movement/relocate game e (fnext path))
      game)))

(defn move-melee-range [game e target limit]
  (let [{:keys [::c/foundation]} (levels/entity-level game e)
        es (vals (:entities (memory/entity-level-memory e)))
        os (set (map ::c/position es))
        paths (sort algebra/PathComparator
                    (remove nil?
                            (pmap #(algebra/a* (::c/position e) % foundation os limit)
                                  (algebra/neighbors (::c/position target)))))]
    (if-let [path (first paths)]
      (movement/relocate game e (fnext path))
      game)))

(defn forget-player [game e player]
  (memory/update-memory game e dissoc :player :player-spotted?))

(defmethod act :serv-bot [game e]
  (let [[type player] (player-known? game e)
        melee (skills/skill e :melee)]
    (world/update game [bot (::c/id e)]
      (when (and (= type :sight) (not (:player-spotted? (memory/entity-memory bot))))
        (-> game
          (transition/transition (transition/sound :serv-bot-spot))
          (memory/update-memory bot assoc :player-spotted? true)))
      (when (= (::c/position bot) (::c/position player))
        (forget-player game bot player))
      (cond (some-> game (player-nearby? bot) combat/attackable?) (combat/melee game bot melee player)
            (= type :sight) (-> game
                              (move-melee-range bot player 100)
                              (memory/update-memory bot assoc :player player))
            (= type :memory) (if-let [path (path-to game bot player 100)]
                               (movement/relocate game bot (first path))
                               (forget-player game bot player))
            :default (move-random game bot)))))

;;; TODO index
(defn system [game]
  (let [es (world/capable-entities game ::c/ai)]
    (reduce act game es)))
