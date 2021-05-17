(defproject rads.food "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[bidi "2.1.6"]
                 [clj-http "3.12.0"]
                 [com.bhauman/figwheel-main "0.2.12"]
                 [com.bhauman/rebel-readline-cljs "0.1.4"]
                 [com.fasterxml.jackson.core/jackson-core "2.10.2"]
                 [com.fasterxml.jackson.core/jackson-databind "2.10.2"]
                 [com.github.seancorfield/honeysql "2.0.0-rc2"]
                 [com.github.seancorfield/next.jdbc "1.2.659"]
                 [com.taoensso/timbre "5.1.2"]
                 [danlentz/clj-uuid "0.1.9"]
                 [day8.re-frame/http-fx "0.2.3"]
                 [etaoin "0.4.1"]
                 [hiccup "1.0.5"]
                 [hickory "0.7.1"]
                 [inflections "0.13.2"]
                 [jarohen/chime "0.3.3"]
                 [liberator "0.15.3"]
                 [medley "1.3.0"]
                 [metosin/jsonista "0.3.3"]
                 [metosin/muuntaja "0.6.8"]
                 [org.clojure/clojure "1.10.3"]
                 [org.clojure/clojurescript "1.10.773"]
                 [org.jsoup/jsoup "1.12.1"]
                 [org.postgresql/postgresql "42.2.20"]
                 [re-frame "1.2.0"]
                 [reagent "1.0.0"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-devel "1.9.3"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [tick "0.4.31-alpha"]
                 [venantius/accountant "0.2.5"]]
  :min-lein-version "2.7.0"
  :plugins [[lein-cljsbuild "1.1.8"]]
  :resource-paths ["target" "resources"]
  :profiles {:dev {:dependencies [[cider/piggieback "0.5.2"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   :source-paths ["src" "dev"]
                   :clean-targets ^{:protect false} ["target"]}
             :uberjar {:prep-tasks [["cljsbuild" "once" "prod"] "compile"]}}
  :cljsbuild
  {:builds
   {:prod
    {:source-paths ["src"]
     :compiler {:main rads.food.browser
                :output-to "target/public/cljs-out/dev-main.js"
                :asset-path "target/public/cljs-out/dev"
                :optimizations :whitespace
                :pretty-print false}}}}
  :uberjar-name "rads.food-standalone.jar")
