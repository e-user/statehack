(ns statehack.system.movement
  (:require [statehack.entity :as entity]
            [statehack.system.render :as render]
            [statehack.system.messages :as messages]
            [statehack.system.world :as world]
            [statehack.system.unique :as unique]
            [statehack.system.input.receivers :as receivers]
            [statehack.system.obstacle :as obstacle]
            [statehack.util :as util]
            [clojure.set :as set]))

(def move-hierarchy (make-hierarchy))

(defn- available-moves-dispatch [game e]
  (-> e :mobile :type))

(defmulti available-moves #'available-moves-dispatch :hierarchy #'move-hierarchy)
(defmethod available-moves :default [& _] nil)

(defn move [game e [x y]]
  (world/update-entity-component game e :position util/matrix-add [x y]))

(defn inbound-moves [game e]
  (let [f (:foundation (world/state game))]
    (set (filter #(render/in-bounds? f (util/matrix-add (:position e) %))
                 world/neighbors))))

(defmethod available-moves :humanoid [game e]
  (let [es (obstacle/filter-obstacles (world/entity-neighbors game e))
        ds (set (map #(world/entity-delta % e) es))]
    (into {} (map (fn [pos] [pos #(move % e pos)])
                  (set/difference (inbound-moves game e) ds)))))

(defn move-next [game sel]
  (let [{:keys [targets]} (:mobile sel)
        es (:entities (world/state game))
        targets (concat (rest targets) [(first targets)])
        e (es (first targets))]
    (-> game
        (world/update-entity-component sel :mobile assoc :targets targets)
        (world/update-entity-component sel :position (constantly (:position e))))))

(defn unavailable-moves [game e]
  (let [os (obstacle/filter-obstacles (world/entity-neighbors game e))
        cs (set/difference world/neighbors (inbound-moves game e))]
    (merge (into {} (map (fn [o] [(world/entity-delta o e) #(messages/log % "There's an obstacle in the way.")]) os))
           (into {} (map (fn [c] [c #(messages/log % "Somehow, you can't move here...")]) cs)))))

(defn update-cursor [game]
  (let [{:keys [entities receivers]} (world/state game)
        r (receivers/current game)
        e (unique/unique-entity game :cursor)
        [x y] (if (entity/capable? r :messages)
                [(+ (count (first (:messages r))) 2) 1]
                (:position r))]
    (world/update-entity-component game e :position (constantly [x y]))))
