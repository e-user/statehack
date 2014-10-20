(ns statehack.util)

(defn matrix-add
  ([x] x)
  ([x y]
     (mapv + x y))
  ([x y & more]
     (reduce matrix-add (matrix-add x y) more)))

(defn matrix-subtract
  ([x] x)
  ([x y]
     (mapv - x y))
  ([x y & more]
     (reduce matrix-subtract (matrix-subtract x y) more)))

(defn index-by [f coll]
  (into {} (map (fn [o] [(f o) o]) coll)))

(defn separate
  [map & ks]
  (let [sep (select-keys map ks)]
    [(apply dissoc map ks) sep]))

(def ^{:doc "A lazy sequence of all natural numbers, including 0"}
  enumeration (iterate inc 0))

(defn enumerate
  "Produce new lazy sequence with each element in `coll` enumerated
  started from 0."
  [coll]
  (map list enumeration coll))

(defn take-while-including
  "Like `take-while`, but includes the element not fulfilling the predicate."
  [pred coll]
  (lazy-seq
   (when-let [s (seq coll)]
     (let [c (first s)]
       (cons c (when (pred c) (take-while-including pred (rest s))))))))
