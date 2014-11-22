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

(ns halo.graphics
  (:require [halo.screen :as screen])
  (:import [com.googlecode.lanterna TerminalSize TextColor$RGB TextColor$Indexed]
           [com.googlecode.lanterna.graphics TextGraphics]))

(defn color
  ([r g b] (TextColor$RGB. r g b))
  ([i] (TextColor$Indexed. i)))

(defn size
  [^TextGraphics graphics]
  (let [^TerminalSize size (.getSize graphics)]
    [(.getColumns size) (.getRows size)]))

(defn set-color
  ([^TextGraphics graphics r g b]
     (.setForegroundColor graphics (color r g b)))
  ([^TextGraphics graphics i]
     (.setForegroundColor graphics (color i))))

(defn put
  ([^TextGraphics graphics s x y]
     (.putString graphics x y s))
  ([^TextGraphics graphics s x y & {:keys [color]}]
     (when color
       (if (sequential? color)
         (apply set-color graphics color)
         (set-color graphics color)))
     (put graphics s x y)))
