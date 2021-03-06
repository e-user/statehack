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

(ns statehack.system.world
  "World manipulation"
  (:refer-clojure :exclude [update])
  (:require [statehack.component :as c]
            [statehack.entity :as entity]))

;;; In order to implement the idea of a purely functional ECS, the current state
;;; of the world is always represented as a head of a sequence. Whenever a
;;; player action causes a new round in the game to start, the current world
;;; state is duplicated and this duplicate manipulated subsequently.

(def store
  "Storage for world state"
  (atom {}))

(defn save
  "Store the current world state into `store`"
  [game]
  (swap! store (constantly (:world game)))
  game)

(defn state
  "Current world state"
  [game]
  (-> game :world first))

(defn entities
  "All entities of current world state"
  [game]
  (:entities (state game)))

(defn entity
  "Lookup entity identified by `id` in current world state"
  [game id]
  ((entities game) id))

(defn lookup-entities
  "Lookup single or multiple entities"
  [game x]
  (if (seq? x)
    (map #(entity game %) x)
    (entity game x)))

(defmacro update
  "Convenience macro to manipulate entities"
  {:arglists '([game [bindings*] updates*])}
  [game bindings & updates]
  {:pre [(vector? bindings) (even? (count bindings))]}
  (let [pairs (partition 2 bindings)
        symbols (map first pairs)
        forms (map second pairs)]
    `(let [ids# [~@forms]]
       (reduce (fn [~game f#] (or (apply f# ~game (map #(lookup-entities ~game %) ids#)) ~game))
         ~game
         [~@(map (fn [body] `(fn [~game ~@symbols] ~body)) updates)]))))

(defn dup-world-state
  "Duplicate and cons the current world state onto the state list"
  [game]
  (let [world (:world game)
        state (first world)]
    (assoc game :world (cons state world))))

(defn update-world-state
  "Update the current world state by running `apply f state args` against it"
  [game f & args]
  (clojure.core/update game :world (fn [[x & xs]] (cons (apply f x args) xs))))

(defn update-in-world-state
  "Run `update-in` with key sequence `ks` against the current world state"
  [game ks f & args]
  (apply update-world-state game update-in ks f args))

(defn update-entities
  "Update game entities with `apply f entities args`"
  [game f & args]
  (apply update-in-world-state game [:entities] f args))

(defn update-entity
  "Update entity identified by `id` in current state with `apply f entity args`
 
  Conform entity to `:statehack/entity` afterwards."
  [game id f & args]
  (update-in-world-state game [:entities id] #(entity/conform (apply f % args))))

(defn remove-entity-component
  "Remove entity components from entity"
  [game id c & cs]
  (apply update-entity game id dissoc c cs))

(defn update-entity-component
  "Update entity's component"
  [game id c f & args]
  (apply update-entity game id clojure.core/update c f args))

(defn update-in-entity-component
  "Update entity component data in entity"
  [game id c ks f & args]
  (apply update-entity game id update-in (cons c ks) f args))

(defn add-entity-component
  "Add components to entity"
  [game id & args]
  (apply update-entity game id assoc args))

(defn add-entity
  "Add entity to game"
  [game e]
  (update-entities game assoc (::c/id e) (entity/conform e)))

(defn remove-entity
  "Remove identity from game"
  [game id]
  (update-entities game #(dissoc % id)))

(defn pop-world-state
  "Pop the current state of the world, if more than one exists"
  [game]
  (update-in game [:world] #(if (> (count %) 1) (next %) %)))

;;; TODO indexes!
(defn capable-entities
  "Find entities owning components"
  [game & cs]
  (entity/filter-capable cs (vals (entities game))))
