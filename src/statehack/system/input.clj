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

(ns statehack.system.input
  "statehack input system"
  (:require [statehack.component :as c]))

(def receive-hierarchy (make-hierarchy))

(defn derive-receive [tag parent]
  (alter-var-root #'receive-hierarchy derive tag parent))

(defn- receive-dispatch [game e input]
  (::c/input e))

(defmulti receive "Receive input for entity"
  #'receive-dispatch :hierarchy #'receive-hierarchy)
