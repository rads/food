# rads.food

An application that combines local DoorDash results with Yelp data into a single interface.

[Click here for a live example showing DoorDash results near my Seattle apartment.](https://food.rads.world)

## Rationale

I order from DoorDash all the time but I prefer to use Yelp for finding new restaurants. I figured I could make an app to combine the two data sources and make my life a little easier.

## Getting Started

### Web Server

1. Set up configuration:

   ```sh
   cp resources/config/dev-example.edn resources/config/dev.edn
   vim resources/config/dev.edn
   ```

1. Start the Clojure REPL:

    ```sh
    lein repl
    ```

2. Start the server:

    ```clojure
    (dev)
    ```

3. Open http://localhost:3000

4. Start the ClojureScript REPL:

    ```clojure
    (cljs)
    ```

### Worker

1. In the Clojure REPL:

   ```clojure
   (require '[rads.food.server.worker :as worker])

   (def w (atom (worker/worker))
   (swap! worker/start w)
   (swap! worker/stop w)
   ```

## Overview

The app uses PostgreSQL for persistence with two primary database clients:

1. a background worker process to fetch DoorDash and Yelp data and write it to the database
2. a web server process to read the database and display the data in a custom UI

The `rads.food.server.worker` namespace is responsible for the background worker. Since DoorDash doesn't provide a developer API, I'm using [Etaoin][etaoin] to log in to a personal account and scrape the info for restaurants near me. After I have the initial info from DoorDash I can look up ratings, photos, and categories from the [Yelp Fusion API][yelp-fusion]. I'm treating the PostgreSQL tables as materialized views over the DoorDash and Yelp results which means I can update the cache as needed by re-running the scraper.

The `rads.food.server.web` namespace is responsible for the web server. Both the worker and the web server use the same database but they don't need to run at the same time or even on the same machine. Once the server is running, it will host a ClojureScript app as well as a minimal EDN-over-HTTP API for client-server communication. You can run the web server on your local machine or host it permanently on a platform like Heroku.

[etaoin]: https://github.com/igrishaev/etaoin
[yelp-fusion]: https://www.yelp.com/developers/documentation/v3