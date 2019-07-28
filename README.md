# Project Description

## Technologies
  1) Minio S3 - raw data storage
  4) NiFi - read data from Minio S3, split and publish to Kafka topic "top-songs"
  5) Kafka - data source for Spark driver program
  2) Spark - load raw data to Minio S3 (ingestion), validate via spark libraries, extract and save primary fields (transformation) [`top-songs-2`]
  3) Hive - aggregate data and persist in new table on Minio S3

## How to Run
 1. run `sbt docker:publishLocal` to build docker image of an application and push it to local registry
 2. run `docker-compose up` to launch Minio S3, Spark master, Spark worker, NiFi, Kafka, Zookeeper and `top-songs-2` spark application docker containers
 3. configure NiFi flow:
   3.1. open `http://localhost:8082/nifi/` in browser
   3.2. Upload Template from `nifi/data/nifi-template-top-songs.xml`
   3.3. add uploaded template Template
   3.4. in ListS3 and FetchS3Object processors set Access Key ID to "minio" and Secret Access Key to "minio123"
   3.5. in SplitRecords processor enable CSVReader and CSVWriter
 4. start NiFi flow

## Workflow
 1. NiFi flow (template file location: `nifi/data/nifi-template-top-songs.xml`)
   <br>1.1. read data from Minio S3 (processors: `ListS3`, `FetchS3Object`)
   <br>1.2. split into csv blocks with up to 10 records (processors: `SplitRecord`)
   <br>1.3. publish to Kafka (processors: `PublishKafka_2_0`)
 2. Spark driver program
   <br>1.1. read CSV blocks (of up to 10 CSV rows) from Kafka topic "top-songs"
   <br>1.2. parse CSV blocks into separate rows
   <br>1.3. process data (parse, validate, clean and transform)
   <br>1.4. save processed data to "s3a://ayegorov/data/Top_1_000_Songs_To_Hear_Before_You_Die/"
 3. Add new 'batch_id' partition to Hive
 4. Count top songs per year before year 2000 in Hive and output to console (aggregation)

## Dataset
Name: Top 1000 Songs To Hear Before You Die
Source: https://opendata.socrata.com/Fun/Top-1-000-Songs-To-Hear-Before-You-Die/ed74-c6ni (included in `top-songs-2` project)
Format: CSV
Delimiter: comma
Schema:
  - THEME - Plain Text
  - TITLE - Plain Text
  - ARTIST - Plain Text
  - YEAR - Number
  - SPOTIFY_URL - Website URL
Description: contains information about selected songs, including artist, publish ygit push -u origin masterear, theme (category) and optionally audio url on Spotify.

## Data Processing
  1. `minio/daga/ayegorov/datasets/Top_1_000_Songs_To_Hear_Before_You_Die/*.csv` are parsed, cleaned and validated by Spark driver program.
    <p>1.1. Parsing (done by Spark library): each line is parsed in separate row; each row is split by comma; each value is set to each column; all values initially assumed as nullable strings.
    <p>1.2. Cleansing: YEAR cleaned from non-numeric symbols.
    <p>1.3. Validation: TITLE and ARTIST should not be null; YEAR should be in range `[1000, <current_year>]`.
  2. Data extracted on step 1 are stored to minio S3.
  3. Extracted data are added as new Hive partition.
Hive aggregation result is printed in console output.
