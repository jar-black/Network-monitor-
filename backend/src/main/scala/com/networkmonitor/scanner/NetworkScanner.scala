package com.networkmonitor.scanner

import cats.effect.IO
import cats.implicits.*
import com.networkmonitor.config.ScannerConfig
import com.networkmonitor.domain.DiscoveredHost
import org.slf4j.LoggerFactory

import java.net.InetAddress
import scala.sys.process.*

/** Scans the local network using ARP / ping sweep to discover active hosts.
  *
  * On a Raspberry Pi this relies on:
  *   - `nmap -sn` (preferred, gives MAC + hostname)
  *   - Falls back to `arp -a` parsing if nmap is unavailable
  */
class NetworkScanner(config: ScannerConfig):

  private val logger = LoggerFactory.getLogger(getClass)

  /** Run a full scan and return discovered hosts. */
  def scan: IO[List[DiscoveredHost]] =
    nmapScan.handleErrorWith { err =>
      IO(logger.warn(s"nmap scan failed (${err.getMessage}), falling back to arp scan")) *>
        arpScan
    }

  /** Preferred: use nmap ping scan which returns IP, MAC, and hostname. */
  private def nmapScan: IO[List[DiscoveredHost]] = IO.blocking {
    logger.info(s"Starting nmap scan on ${config.networkCidr}")
    val output = s"nmap -sn ${config.networkCidr}".!!
    NmapParser.parse(output)
  }

  /** Fallback: parse the system ARP table. */
  private def arpScan: IO[List[DiscoveredHost]] = IO.blocking {
    logger.info("Starting ARP table scan")
    // First do a ping sweep to populate the ARP table
    val cidrBase = config.networkCidr.takeWhile(_ != '/')
    val baseOctets = cidrBase.split('.').take(3).mkString(".")
    (1 to 254).toList.foreach { i =>
      try {
        val addr = InetAddress.getByName(s"$baseOctets.$i")
        addr.isReachable(config.pingTimeoutMs)
      } catch { case _: Exception => () }
    }
    val arpOutput = "arp -a".!!
    ArpParser.parse(arpOutput)
  }

/** Parses nmap -sn output into DiscoveredHost entries. */
object NmapParser:
  private val hostBlockRegex = """(?s)Nmap scan report for (.+?)\n(.*?)(?=Nmap scan report|\z)""".r
  private val ipRegex        = """(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""".r
  private val macRegex       = """MAC Address:\s+([0-9A-Fa-f:]{17})""".r

  def parse(output: String): List[DiscoveredHost] =
    hostBlockRegex.findAllMatchIn(output).flatMap { m =>
      val header = m.group(1)
      val body   = m.group(2)
      for
        ip  <- ipRegex.findFirstIn(header)
        mac <- macRegex.findFirstMatchIn(body).map(_.group(1))
      yield
        val hostname = header.split("\\s").headOption
          .filterNot(h => ipRegex.matches(h))
        DiscoveredHost(ip, mac.toUpperCase, hostname)
    }.toList

/** Parses `arp -a` output into DiscoveredHost entries. */
object ArpParser:
  // Example line: ? (192.168.1.1) at aa:bb:cc:dd:ee:ff [ether] on eth0
  private val arpLineRegex =
    """(?:(\S+)\s+)?\((\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\)\s+at\s+([0-9a-fA-F:]{17})""".r

  def parse(output: String): List[DiscoveredHost] =
    arpLineRegex.findAllMatchIn(output).map { m =>
      val hostname = Option(m.group(1)).filter(_ != "?")
      val ip       = m.group(2)
      val mac      = m.group(3).toUpperCase
      DiscoveredHost(ip, mac, hostname)
    }.toList
