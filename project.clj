(defproject clj-turing-test "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.54"]

                 [http-kit "2.6.0"]
                 [ring "1.9.2"]
                 [bidi "2.1.6"]

                 [cheshire "5.11.0"]
                 [hiccup "1.0.5"]

                 [reagent "1.0.0"]]
  :hooks [leiningen.cljsbuild]
  :plugins [[lein-cljsbuild "1.1.8"]]
  :cljsbuild {:builds
              [{:source-paths ["src/clj_turing_test/front_end"]
                :compiler {:output-to "resources/turing.js"
                           :optimizations :whitespace
                           :pretty-print true}
                :jar true}]}

  :repl-options {:init-ns clj-turing-test.core})
