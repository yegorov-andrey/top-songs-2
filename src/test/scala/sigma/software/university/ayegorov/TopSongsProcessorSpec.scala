package sigma.software.university.ayegorov

import java.util.Calendar

import org.apache.spark.sql.catalyst.expressions.GenericRow
import org.scalatest.FlatSpec

class TopSongsProcessorSpec extends FlatSpec {

  it should "parse row" in {
    val row = new GenericRow(Array[Any]("some_theme", "some_title", "some_artist", "some_year", "some_spotifyUrl"))
    val song = TopSongsProcessor.parseData(row)
    assert(song.theme === "some_theme")
    assert(song.title === "some_title")
    assert(song.artist === "some_artist")
    assert(song.year === "some_year")
    assert(song.spotifyUrl === "some_spotifyUrl")
  }

  it should "clean data" in {
    val dirtySong = Song("some_theme", "some_title", "some_artist", "a 19-c38~", "some_spotifyUrl")
    val cleanSong = TopSongsProcessor.cleanData(dirtySong)
    assert(cleanSong.year === "1938")
  }

  it should "be valid" in {
    val song = Song("some_theme", "some_title", "some_artist", "1000", "some_spotifyUrl")
    val valid = TopSongsProcessor.validateData(song)
    assert(valid === true)
  }

  it should "transform Song for Hive" in {
    val song = Song("some_theme", "some_title", "some_artist", "some_year", "some_spotifyUrl")
    val record = TopSongsProcessor.transformData(song)
    assert(record === "some_artist\tsome_title\tsome_year")
  }

  "Title" should "not be null" in {
    val song = Song("some_theme", null, "some_artist", "1000", "some_spotifyUrl")
    val valid = TopSongsProcessor.validateData(song)
    assert(valid === false)
  }

  "Artist" should "not be null" in {
    val song = Song("some_theme", "some_title", null, "1000", "some_spotifyUrl")
    val valid = TopSongsProcessor.validateData(song)
    assert(valid === false)
  }

  "Year below 1000" should "be invalid" in {
    val song = Song("some_theme", "some_title", "some_artist", "999", "some_spotifyUrl")
    val valid = TopSongsProcessor.validateData(song)
    assert(valid === false)
  }

  "Year above current" should "be invalid" in {
    val nextYear = Calendar.getInstance.get(Calendar.YEAR) + 1
    val song = Song("some_theme", "some_title", "some_artist", nextYear.toString, "some_spotifyUrl")
    val valid = TopSongsProcessor.validateData(song)
    assert(valid === false)
  }
}
