(defproject statehack "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 ;[clojure-lanterna "0.9.4"]
                 [org.clojars.folcon/clojure-lanterna "0.9.5"]]
  ;:main ^:skip-aot statehack.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :main statehack.core}})
