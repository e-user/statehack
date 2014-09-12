(ns statehack.game
  (:require [statehack.input :as input]
            [statehack.ui :as ui]
            [statehack.util :as util]
            [statehack.entity.player :as player]
            [statehack.entity.room :as room]
            [lanterna.screen :as screen]))

(def first-room
"XoXXXoXXXXX
o         X
X         X
X         X
X         X
X         X
XXXXXXXXXXX")

(defn new-game [scr]
  (let [{:keys [id] :as player} (player/player 40 18)]
    {:screen scr
     :viewport [0 0]
     :world [{:mode :world
              :foundation (ui/space 80 24)
              :player id
              :receivers [id]
              :entities (util/index-by :id
                                       (flatten
                                        [player
                                         (room/extract-room first-room 35 13)]))}]}))

(defn load-game [scr world]
  {:screen scr
   :viewport [0 0]
   :world world})

(comment
  (game/run scr (game/load-game scr @statehack.game.world/state)))

(defn run
  ([scr game]
     (doall (take-while identity (repeatedly #(screen/get-key scr))))
     (screen/in-screen scr
       (loop [input nil game game]
         (print (prn-str input))
         (when (not= input :escape)
           (let [game (-> game (input/input-system input) ui/render-system)]
             (recur (screen/get-key-blocking scr) game))))))
  ([scr]
     (run scr (new-game scr))))
