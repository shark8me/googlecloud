(ns googlecloud.bigquery.tables
  (:require [googlecloud.core :as gc]
            [googlecloud.bigquery.coerce]
            [schema.core :as s])
  (:import  [com.google.api.services.bigquery.model TableReference TableFieldSchema TableSchema Table TimePartitioning]))

(defn list [service project-id dataset-id]
  (letfn [(mk-list-op
            ([] (-> service (.tables) (.list project-id dataset-id)))
            ([page-token] (doto (mk-list-op)
                            (.setPageToken page-token))))]
    (->> (gc/lazy-paginate-seq mk-list-op #(.getTables %)) (map gc/to-clojure))))

(defn get [service project-id dataset-id table-id]
  (-> service (.tables) (.get project-id dataset-id table-id) (.execute) (gc/to-clojure)))


(def table-reference-schema
  "A reference for a table in a dataset"
  {:table-id s/Str
   :project-id s/Str
   :dataset-id s/Str})

(defn- mk-table-reference [{:keys [table-id project-id dataset-id] :as ref}]
  (doto (TableReference. )
    (.setProjectId project-id)
    (.setDatasetId dataset-id)
    (.setTableId   table-id)))

(def table-field-schema {:name                         s/Str
                         (s/optional-key :description) s/Str
                         :type                         (s/enum :string :integer :float :boolean :timestamp :record)
                         (s/optional-key :mode)        (s/enum :nullable :required :repeated)
                         (s/optional-key :fields)      [(s/recursive #'table-field-schema)]})

(def table-schema
  "BigQuery Table schema"
  {:table-reference                table-reference-schema
   (s/optional-key :description)   (s/maybe s/Str)
   (s/optional-key :friendly-name) (s/maybe s/Str)
   (s/optional-key :schema)        [table-field-schema]})

(def field-type {:string "STRING"
                 :integer "INTEGER"
                 :float "FLOAT"
                 :boolean "BOOLEAN"
                 :timestamp "TIMESTAMP"
                 :record "RECORD"})

(def field-mode {:nullable "NULLABLE"
                 :required "REQUIRED"
                 :repeated "REPEATED"})

(defn- mk-fields [{:keys [name type mode description fields]
                   :or   {mode :nullable}}]
  (let [s (TableFieldSchema. )]
    (.setName s name)
    (.setDescription s description)
    (.setType s (field-type type))
    (when fields
      (.setFields s (map mk-fields fields)))
    (when mode
      (.setMode s (field-mode mode)))
    s))

(defn- mk-schema [schema]
  (doto (TableSchema.)
    (.setFields (map mk-fields schema))))

(defn- mk-partition []
  (doto (TimePartitioning. ) 
    (.setType "DAY")))

(defn- mk-table [{:keys [table-reference description schema friendly-name] :as table}]
  {:pre [(s/validate table-schema table)]}
  (doto (Table. )
    (.setTableReference (mk-table-reference table-reference))
    (.setDescription    description)
    (.setFriendlyName   friendly-name)
    (.setSchema         (mk-schema schema))
    (.setTimePartitioning   (mk-partition))))

(defn insert [service {:keys [table-reference] :as table}]
  (let [op (-> service
               (.tables)
               (.insert (:project-id table-reference) (:dataset-id table-reference) (mk-table table)))]
    (gc/to-clojure (.execute op))))

(defn update [service {:keys [table-reference] :as table}]
  (let [op (-> service
               (.tables)
               (.update 
                (:project-id table-reference) 
                (:dataset-id table-reference) 
                (:table-id table-reference)
                (mk-table table)))]
    (gc/to-clojure (.execute op))))

(defn delete [service project-id dataset-id table-id]
  (let [delete-op (-> service (.tables) (.delete project-id dataset-id table-id))]
    (.execute delete-op)))
