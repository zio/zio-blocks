package zio.blocks.schema.xml

import zio.test._

object XmlRoundTripSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("XmlRoundTripSpec")(
    test("RSS feed round-trip") {
      val rssXml =
        """<?xml version="1.0" encoding="UTF-8"?><rss xmlns:atom="http://www.w3.org/2005/Atom" version="2.0"><channel><title><![CDATA[BBC News]]></title><description><![CDATA[BBC News - World]]></description><link>https://www.bbc.co.uk/news/world</link><image><url>https://news.bbcimg.co.uk/nol/shared/img/bbc_news_120x60.gif</url><title>BBC News</title><link>https://www.bbc.co.uk/news/world</link></image><lastBuildDate>Sun, 08 Feb 2026 15:51:39 GMT</lastBuildDate><atom:link href="https://feeds.bbci.co.uk/news/world/rss.xml" rel="self" type="application/rss+xml"/><item><title><![CDATA[Japan's governing party projected to win snap election]]></title><description><![CDATA[A coalition led by current PM is expected to clinch a decisive win.]]></description><link>https://www.bbc.com/news/articles/cx2y7d2z29xo</link><guid isPermaLink="false">https://www.bbc.com/news/articles/cx2y7d2z29xo#0</guid><pubDate>Sun, 08 Feb 2026 14:23:19 GMT</pubDate></item><item><title><![CDATA[Polls close in Thai election]]></title><description><![CDATA[Thai voters choose between sweeping change or more of the same.]]></description><link>https://www.bbc.com/news/articles/cx2jn4z4eq0o</link><guid isPermaLink="false">https://www.bbc.com/news/articles/cx2jn4z4eq0o#0</guid><pubDate>Sun, 08 Feb 2026 14:47:10 GMT</pubDate></item></channel></rss>"""

      val parsed1Result = XmlReader.read(rssXml)
      val parsed1       = parsed1Result match {
        case Right(xml) => xml
        case Left(err)  => throw new Exception(s"Failed to parse RSS XML: ${err.getMessage}")
      }
      val writerConfig  = WriterConfig(indentStep = 0)
      val written       = XmlWriter.write(parsed1, writerConfig)
      val parsed2Result = XmlReader.read(written)
      val parsed2       = parsed2Result.getOrElse(throw new Exception("Failed to parse written RSS XML"))
      assertTrue(parsed1 == parsed2)
    },
    test("Atom feed round-trip") {
      val atomXml =
        """<?xml version="1.0" encoding="UTF-8"?><feed xmlns="http://www.w3.org/2005/Atom" xml:lang="en-US"><id>tag:github.com,2008:https://github.com/zio/zio/releases</id><link type="text/html" rel="alternate" href="https://github.com/zio/zio/releases"/><link type="application/atom+xml" rel="self" href="https://github.com/zio/zio/releases.atom"/><title>Release notes from zio</title><updated>2025-12-28T03:25:46Z</updated><entry><id>tag:github.com,2008:Repository/134079884/v2.1.24</id><updated>2025-12-29T19:57:55Z</updated><link rel="alternate" type="text/html" href="https://github.com/zio/zio/releases/tag/v2.1.24"/><title>2.1.24</title><content type="html">This release focuses on performance improvements.</content></entry><entry><id>tag:github.com,2008:Repository/134079884/v2.1.23</id><updated>2025-11-15T10:30:00Z</updated><link rel="alternate" type="text/html" href="https://github.com/zio/zio/releases/tag/v2.1.23"/><title>2.1.23</title><content type="html">Bug fixes and stability improvements.</content></entry></feed>"""

      val parsed1Result = XmlReader.read(atomXml)
      val parsed1       = parsed1Result match {
        case Right(xml) => xml
        case Left(err)  => throw new Exception(s"Failed to parse Atom XML: ${err.getMessage}")
      }
      val writerConfig  = WriterConfig(indentStep = 0)
      val written       = XmlWriter.write(parsed1, writerConfig)
      val parsed2Result = XmlReader.read(written)
      val parsed2       = parsed2Result.getOrElse(throw new Exception("Failed to parse written Atom XML"))
      assertTrue(parsed1 == parsed2)
    },
    test("Sitemap round-trip") {
      val sitemapXml =
        """<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9"><url><loc>https://www.example.org/</loc><lastmod>2024-01-15</lastmod></url><url><loc>https://www.example.org/about</loc><lastmod>2024-01-10</lastmod></url><url><loc>https://www.example.org/products</loc><lastmod>2024-01-20</lastmod></url><url><loc>https://www.example.org/contact</loc><lastmod>2024-01-05</lastmod></url></urlset>"""

      val parsed1Result = XmlReader.read(sitemapXml)
      val parsed1       = parsed1Result match {
        case Right(xml) => xml
        case Left(err)  => throw new Exception(s"Failed to parse Sitemap XML: ${err.getMessage}")
      }
      val writerConfig  = WriterConfig(indentStep = 0)
      val written       = XmlWriter.write(parsed1, writerConfig)
      val parsed2Result = XmlReader.read(written)
      val parsed2       = parsed2Result.getOrElse(throw new Exception("Failed to parse written Sitemap XML"))
      assertTrue(parsed1 == parsed2)
    }
  )
}
