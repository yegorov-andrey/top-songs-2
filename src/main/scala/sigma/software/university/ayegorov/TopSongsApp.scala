package sigma.software.university.ayegorov

import java.util.Calendar

import com.univocity.parsers.csv.{Csv, CsvParser}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkConf
import org.apache.spark.sql.catalyst.encoders.{ExpressionEncoder, RowEncoder}
import org.apache.spark.sql.catalyst.expressions.GenericRow
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.sql.{Dataset, _}
import sigma.software.university.ayegorov.TopSongsHiveHelper.{addBatch, countTopSongsPerYearBefore2000}
import sigma.software.university.ayegorov.TopSongsProcessor.{cleanData, parseData, transformData, validateData}

import scala.util.Properties

object TopSongsApp {

  Logger.getRootLogger.setLevel(Level.ERROR)
  Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
  Logger.getLogger("org.spark-project").setLevel(Level.WARN)
  Logger.getLogger("org.apache.kafka.clients.consumer").setLevel(Level.WARN)
  private val log = Logger.getLogger(getClass)
  log.setLevel(Level.INFO)

  val s3aUrl = "http://minio:9000"
  val s3aAccessKey = "minio"
  val s3aSecretKey = "minio123"
  var bucket = "ayegorov"

  def main(args: Array[String]): Unit = {

    val spark = SparkSession
      .builder
      .appName("TopSongs2")
      .enableHiveSupport
      .getOrCreate
    val sc = spark.sparkContext

    val hc = sc.hadoopConfiguration
    hc.set("fs.s3a.endpoint", s3aUrl)
    hc.set("fs.s3a.access.key", s3aAccessKey)
    hc.set("fs.s3a.secret.key", s3aSecretKey)
    hc.set("fs.s3a.path.style.access", "true")

    val topic = Properties.envOrElse("KAFKA_TOPIC", "top-songs")
    log.info(s"Kafka topic: $topic")

    val ds = readRowsFromKafka(spark, topic)
    processSongRows(spark, ds)
  }

  // read batches of rows from kafka and split into separate rows
  def readRowsFromKafka(spark: SparkSession, kafkaTopic: String): Dataset[Row] = {
    implicit val stringEncoder: Encoder[String] = Encoders.STRING
    spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "kafka:9092")
      .option("subscribe", kafkaTopic)
      //.option("startingOffsets", "earliest")
      .load
      .selectExpr("CAST(value AS STRING)")
      .as(Encoders.STRING)
      .flatMap(value => value.split("\n"))
      .transform((ds: Dataset[String]) => {
        val schema = StructType(Seq(
          StructField("THEME", StringType),
          StructField("TITLE", StringType),
          StructField("ARTIST", StringType),
          StructField("YEAR", StringType),
          StructField("SPOTIFY_URL", StringType, nullable = true)
        ))
        implicit val encoder: ExpressionEncoder[Row] = RowEncoder(schema)
        ds.map(line => {
          val values = new CsvParser(Csv.parseExcel()).parseLine(line)
          new GenericRow(values.asInstanceOf[Array[Any]]).asInstanceOf[Row]
        })
      })
  }

  // parse, clean, validate, transform and persist data into Hive
  def processSongRows(spark: SparkSession, ds: Dataset[Row]): Unit = {
    import spark.sqlContext.implicits._
    val persistOutput = ds.writeStream
      .foreachBatch((batchDS: Dataset[Row], _: Long) => {
        val batchId = s"batch_id=${Calendar.getInstance.getTimeInMillis.toString}"
        log.info(s"Batch ID: $batchId")
        val outputPath = s"s3a://$bucket/data/Top_1_000_Songs_To_Hear_Before_You_Die/$batchId"
        batchDS
          .rdd
          .map(parseData)
          .map(cleanData)
          .filter(validateData)
          .map(transformData)
          .toDF
          .write
          .text(outputPath)
        log.info(s"Transformed data and saved to $outputPath")

        addBatch(spark.sql, outputPath, batchId)
        countTopSongsPerYearBefore2000(spark.sql)
          .foreach((yearCount: (Int, Long)) => println(s"${yearCount._1} -> ${yearCount._2}"))
      })
      .start

    persistOutput.awaitTermination()
  }
}
