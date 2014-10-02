(ns statehack.game
  (:require [statehack.system.input :as input]
            [statehack.system.render :as render]
            [statehack.util :as util]
            [statehack.entity.player :as player]
            [statehack.entity.status-bar :as status]
            [statehack.entity.bot :as bot]
            [statehack.entity.cursor :as cursor]
            [statehack.entity.room :as room]
            [lanterna.screen :as screen]))

(def first-room
"XOXXXoXXXXX
O         X
X         X
X         X
X         X
X         X
XXXXXXXXXXX")

(defn new-game [scr]
  (let [{:keys [id] :as player} (player/player "Malefeitor" 40 18 10)]
    {:screen scr
     :viewport [0 0]
     :world [{:foundation (render/space 80 24)
              :receivers [id]
              :entities (util/index-by :id
                                       (flatten
                                        [player
                                         (status/status-bar)
                                         (cursor/cursor)
                                         (room/extract-room first-room 35 13)
                                         (bot/bot 40 10 5)]))}]}))

(defn load-game [scr world]
  {:screen scr
   :viewport [0 0]
   :world world})

(comment
  (game/run scr (game/load-game scr @statehack.system.world/state)))

(defn run
  ([screen game]
     (doall (take-while identity (repeatedly #(screen/get-key screen))))
     (screen/in-screen screen
       (loop [input nil game (render/system game)]
         (let [{:keys [quit time] :as game} (-> game (input/player-turn input) render/system)]
           (when-not quit
             (let [game (if time game game)]
               (recur (screen/get-key-blocking screen)
                      (dissoc game :quit :time))))))))
  ([screen]
     (run screen (new-game screen))))
