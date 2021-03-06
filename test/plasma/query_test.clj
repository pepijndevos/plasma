(ns plasma.query-test
  (:use [plasma util graph viz query operator exec construct]
        [clojure test stacktrace]
        lamina.core
        clojure.contrib.generic.math-functions
        test-utils)
  (:require [logjam.core :as log]))

(def TIMEOUT 200)

(use-fixtures :each test-fixture)

(deftest path-test-manual
  (let [plan (path [:music :synths :synth])
        plan (with-result-project plan)
        tree (query-tree plan TIMEOUT)]
    (run-query tree {})
    (is (= #{:kick :bass :snare :hat}
           (set (map (comp :label find-node :id)
                     (query-tree-results tree)))))))

(deftest path-query-test
  (let [res (query (path [:music :synths :synth]))
        res2 (query (path [ROOT-ID :music :synths :synth]))
        synths (:id (first (query (path [:music :synths]))))
        res3 (query (path [synths :synth]))
        res4 (query (path [ROOT-ID :music :synths {:label :synth :favorite true}]))]
    (is (apply = #{:kick :bass :snare :hat}
               (map #(set (map (comp :label find-node :id) %)) [res res2 res3])))
    (is (= :bass (first (map (comp :label find-node :id) res4))))))

(deftest nested-path-test
  (let [q1 (path [:music :synths])
        q2 (-> (path [s [q1 :synth]])
             (project ['s :label]))
        res (query q2)]
    (log/to :flow (with-out-str (print-query q2)))
    (is (= #{:kick :bass :snare :hat}
               (set (map :label res))))))


(defmacro run-time
  "Evaluates expr and returns the time it took to execute in ms."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     (/ (double (- (. System (nanoTime)) start#)) 1000000.0)))

(deftest where-query-test
  (let [q (-> (path [s [:music :synths :synth]])
            (where (> (* 100 (:score 's)) (/ (inc 299) (sqrt 100)))))
        q2 (project q ['s :label])]
    (is (every? uuid? (map :id (query q))))
    (is (= #{:kick :bass :snare}
           (set (map :label (query q2))))))
  (let [p (-> (path [sesh [:sessions :session]
                     synth [sesh :synth]])
            (where (= (:label 'synth) :kick))
            (project ['sesh :label]))]
    (is (= #{:red-pill :take-six}
           (set (map :label (query p))))))
  (is (thrown? Exception (query (-> (path [:sessions :session])
                                  (where (= (:foo 'bogus) :asdf)))))))

(deftest auto-project-test
  (let [p (-> (path [sesh [:sessions :session]
                     synth [sesh :synth]])
            (where (= (:label 'synth) :kick)))]
    (is (false? (#'plasma.query/has-projection? p)))
    (is (true? (#'plasma.query/has-projection? (project p ['sesh :label]))))))

(deftest project-test
  (let [q (-> (path [synth [:music :synths :synth]])
            (project ['synth :label]))
        q2 (-> (path [synth [:music :synths :synth]])
             (project 'synth))]
    (is (= #{:kick :bass :snare :hat}
           (set (map :label (query q)))))
    (is (= #{:kick :bass :snare :hat}
           (set (map (comp :label find-node :id) (query q2)))))))

(deftest count-test
  (let [q (count* (limit (path [synth [:music :synths :synth]])
                 2))]
    (is (= 2 (first (query q))))))

(deftest average-test
  (let [q (-> (path [synth [:music :synths :synth]])
            (avg 'synth :score))]
    (is (= (average [0.8 0.3 0.4 0.6]) (first (query q))))))

(deftest limit-test
  (let [q (limit (path [synth [:music :synths :synth]])
                 2)]
    (is (= 2 (count (query q))))))

(deftest choose-test
  (let [q (choose (path [synth [:music :synths :synth]])
                 2)]
    (is (= 2 (count (query q))))))

(deftest order-by-test
  (let [q1 (-> (path [synth [:music :synths :synth]])
             (order-by 'synth :score)
             (project ['synth :label :score]))
        q2 (-> (path [synth [:music :synths :synth]])
             (order-by 'synth :score :desc)
             (project ['synth :label :score]))
        r1 (query q1)
        r2 (query q2)]
    (is (= 4 (count r1) (count r2)))
    (is (= r1 (reverse r2)))))

(defn- build-test-graph
  []
  (construct* (-> (nodes [root ROOT-ID
                          base :base
                          a {:score 9}
                          b {:score 8}
                          c {:score 2}
                          d {:score 4}])
                (edges [root base :foo
                        base a :bar
                        base b :bar
                        base c :bar
                        base d :bar]))))

(deftest delete-test
  (clear-graph)
  (build-test-graph)
  (is (= 4 (count (query (path [bar [:foo :bar]])))))
  (query-delete (-> (path [bar [:foo :bar]])
                  (where (> (:score 'bar) 5))))
  (is (= 2 (count (query (path [:foo :bar]))))))

(comment deftest recurse-test
  (recurse (path [:net :peer])
           :count 10))

