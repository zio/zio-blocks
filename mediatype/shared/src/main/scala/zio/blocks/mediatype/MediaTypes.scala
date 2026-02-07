package zio.blocks.mediatype

// AUTO-GENERATED - DO NOT EDIT
// Generated from https://github.com/jshttp/mime-db
// Run: sbt generateMediaTypes

object MediaTypes {
  import zio.blocks.mediatype.MediaType

  lazy val any: MediaType = MediaType("*", "*")

  object application {
    lazy val _1dInterleavedParityfec: MediaType =
      MediaType("application", "1d-interleaved-parityfec", compressible = false, binary = true)

    lazy val _3gpdashQoeReportplusxml: MediaType =
      MediaType("application", "3gpdash-qoe-report+xml", compressible = true, binary = true)

    lazy val _3gppImsplusxml: MediaType =
      MediaType("application", "3gpp-ims+xml", compressible = true, binary = true)

    lazy val _3gppMbsObjectManifestplusjson: MediaType =
      MediaType("application", "3gpp-mbs-object-manifest+json", compressible = true, binary = false)

    lazy val _3gppMbsUserServiceDescriptionsplusjson: MediaType =
      MediaType("application", "3gpp-mbs-user-service-descriptions+json", compressible = true, binary = false)

    lazy val _3gppMediaDeliveryMetricsReportplusjson: MediaType =
      MediaType("application", "3gpp-media-delivery-metrics-report+json", compressible = true, binary = false)

    lazy val _3gpphalplusjson: MediaType =
      MediaType("application", "3gpphal+json", compressible = true, binary = false)

    lazy val _3gpphalformsplusjson: MediaType =
      MediaType("application", "3gpphalforms+json", compressible = true, binary = false)

    lazy val a2l: MediaType =
      MediaType("application", "a2l", compressible = false, binary = true)

    lazy val acepluscbor: MediaType =
      MediaType("application", "ace+cbor", compressible = false, binary = true)

    lazy val aceplusjson: MediaType =
      MediaType("application", "ace+json", compressible = true, binary = false)

    lazy val aceGroupcommpluscbor: MediaType =
      MediaType("application", "ace-groupcomm+cbor", compressible = false, binary = true)

    lazy val aceTrlpluscbor: MediaType =
      MediaType("application", "ace-trl+cbor", compressible = false, binary = true)

    lazy val activemessage: MediaType =
      MediaType("application", "activemessage", compressible = false, binary = true)

    lazy val activityplusjson: MediaType =
      MediaType("application", "activity+json", compressible = true, binary = false)

    lazy val aifpluscbor: MediaType =
      MediaType("application", "aif+cbor", compressible = false, binary = true)

    lazy val aifplusjson: MediaType =
      MediaType("application", "aif+json", compressible = true, binary = false)

    lazy val altoCdniplusjson: MediaType =
      MediaType("application", "alto-cdni+json", compressible = true, binary = false)

    lazy val altoCdnifilterplusjson: MediaType =
      MediaType("application", "alto-cdnifilter+json", compressible = true, binary = false)

    lazy val altoCostmapplusjson: MediaType =
      MediaType("application", "alto-costmap+json", compressible = true, binary = false)

    lazy val altoCostmapfilterplusjson: MediaType =
      MediaType("application", "alto-costmapfilter+json", compressible = true, binary = false)

    lazy val altoDirectoryplusjson: MediaType =
      MediaType("application", "alto-directory+json", compressible = true, binary = false)

    lazy val altoEndpointcostplusjson: MediaType =
      MediaType("application", "alto-endpointcost+json", compressible = true, binary = false)

    lazy val altoEndpointcostparamsplusjson: MediaType =
      MediaType("application", "alto-endpointcostparams+json", compressible = true, binary = false)

    lazy val altoEndpointpropplusjson: MediaType =
      MediaType("application", "alto-endpointprop+json", compressible = true, binary = false)

    lazy val altoEndpointpropparamsplusjson: MediaType =
      MediaType("application", "alto-endpointpropparams+json", compressible = true, binary = false)

    lazy val altoErrorplusjson: MediaType =
      MediaType("application", "alto-error+json", compressible = true, binary = false)

    lazy val altoNetworkmapplusjson: MediaType =
      MediaType("application", "alto-networkmap+json", compressible = true, binary = false)

    lazy val altoNetworkmapfilterplusjson: MediaType =
      MediaType("application", "alto-networkmapfilter+json", compressible = true, binary = false)

    lazy val altoPropmapplusjson: MediaType =
      MediaType("application", "alto-propmap+json", compressible = true, binary = false)

    lazy val altoPropmapparamsplusjson: MediaType =
      MediaType("application", "alto-propmapparams+json", compressible = true, binary = false)

    lazy val altoTipsplusjson: MediaType =
      MediaType("application", "alto-tips+json", compressible = true, binary = false)

    lazy val altoTipsparamsplusjson: MediaType =
      MediaType("application", "alto-tipsparams+json", compressible = true, binary = false)

    lazy val altoUpdatestreamcontrolplusjson: MediaType =
      MediaType("application", "alto-updatestreamcontrol+json", compressible = true, binary = false)

    lazy val altoUpdatestreamparamsplusjson: MediaType =
      MediaType("application", "alto-updatestreamparams+json", compressible = true, binary = false)

    lazy val aml: MediaType =
      MediaType("application", "aml", compressible = false, binary = true)

    lazy val andrewInset: MediaType =
      MediaType("application", "andrew-inset", compressible = false, binary = true, fileExtensions = List("ez"))

    lazy val appinstaller: MediaType =
      MediaType(
        "application",
        "appinstaller",
        compressible = false,
        binary = true,
        fileExtensions = List("appinstaller")
      )

    lazy val applefile: MediaType =
      MediaType("application", "applefile", compressible = false, binary = true)

    lazy val applixware: MediaType =
      MediaType("application", "applixware", compressible = false, binary = true, fileExtensions = List("aw"))

    lazy val appx: MediaType =
      MediaType("application", "appx", compressible = false, binary = true, fileExtensions = List("appx"))

    lazy val appxbundle: MediaType =
      MediaType("application", "appxbundle", compressible = false, binary = true, fileExtensions = List("appxbundle"))

    lazy val asyncapiplusjson: MediaType =
      MediaType("application", "asyncapi+json", compressible = true, binary = false)

    lazy val asyncapiplusyaml: MediaType =
      MediaType("application", "asyncapi+yaml", compressible = false, binary = true)

    lazy val atplusjwt: MediaType =
      MediaType("application", "at+jwt", compressible = false, binary = true)

    lazy val atf: MediaType =
      MediaType("application", "atf", compressible = false, binary = true)

    lazy val atfx: MediaType =
      MediaType("application", "atfx", compressible = false, binary = true)

    lazy val atomplusxml: MediaType =
      MediaType("application", "atom+xml", compressible = true, binary = true, fileExtensions = List("atom"))

    lazy val atomcatplusxml: MediaType =
      MediaType("application", "atomcat+xml", compressible = true, binary = true, fileExtensions = List("atomcat"))

    lazy val atomdeletedplusxml: MediaType =
      MediaType(
        "application",
        "atomdeleted+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("atomdeleted")
      )

    lazy val atomicmail: MediaType =
      MediaType("application", "atomicmail", compressible = false, binary = true)

    lazy val atomsvcplusxml: MediaType =
      MediaType("application", "atomsvc+xml", compressible = true, binary = true, fileExtensions = List("atomsvc"))

    lazy val atscDwdplusxml: MediaType =
      MediaType("application", "atsc-dwd+xml", compressible = true, binary = true, fileExtensions = List("dwd"))

    lazy val atscDynamicEventMessage: MediaType =
      MediaType("application", "atsc-dynamic-event-message", compressible = false, binary = true)

    lazy val atscHeldplusxml: MediaType =
      MediaType("application", "atsc-held+xml", compressible = true, binary = true, fileExtensions = List("held"))

    lazy val atscRdtplusjson: MediaType =
      MediaType("application", "atsc-rdt+json", compressible = true, binary = false)

    lazy val atscRsatplusxml: MediaType =
      MediaType("application", "atsc-rsat+xml", compressible = true, binary = true, fileExtensions = List("rsat"))

    lazy val atxml: MediaType =
      MediaType("application", "atxml", compressible = false, binary = true)

    lazy val authPolicyplusxml: MediaType =
      MediaType("application", "auth-policy+xml", compressible = true, binary = true)

    lazy val automationmlAmlplusxml: MediaType =
      MediaType("application", "automationml-aml+xml", compressible = true, binary = true, fileExtensions = List("aml"))

    lazy val automationmlAmlxpluszip: MediaType =
      MediaType(
        "application",
        "automationml-amlx+zip",
        compressible = false,
        binary = true,
        fileExtensions = List("amlx")
      )

    lazy val bacnetXddpluszip: MediaType =
      MediaType("application", "bacnet-xdd+zip", compressible = false, binary = true)

    lazy val batchSmtp: MediaType =
      MediaType("application", "batch-smtp", compressible = false, binary = true)

    lazy val bdoc: MediaType =
      MediaType("application", "bdoc", compressible = false, binary = true, fileExtensions = List("bdoc"))

    lazy val beepplusxml: MediaType =
      MediaType("application", "beep+xml", compressible = true, binary = true)

    lazy val bufr: MediaType =
      MediaType("application", "bufr", compressible = false, binary = true)

    lazy val c2pa: MediaType =
      MediaType("application", "c2pa", compressible = false, binary = true)

    lazy val calendarplusjson: MediaType =
      MediaType("application", "calendar+json", compressible = true, binary = false)

    lazy val calendarplusxml: MediaType =
      MediaType("application", "calendar+xml", compressible = true, binary = true, fileExtensions = List("xcs"))

    lazy val callCompletion: MediaType =
      MediaType("application", "call-completion", compressible = false, binary = true)

    lazy val cals1840: MediaType =
      MediaType("application", "cals-1840", compressible = false, binary = true)

    lazy val captiveplusjson: MediaType =
      MediaType("application", "captive+json", compressible = true, binary = false)

    lazy val cbor: MediaType =
      MediaType("application", "cbor", compressible = false, binary = true)

    lazy val cborSeq: MediaType =
      MediaType("application", "cbor-seq", compressible = false, binary = true)

    lazy val cccex: MediaType =
      MediaType("application", "cccex", compressible = false, binary = true)

    lazy val ccmpplusxml: MediaType =
      MediaType("application", "ccmp+xml", compressible = true, binary = true)

    lazy val ccxmlplusxml: MediaType =
      MediaType("application", "ccxml+xml", compressible = true, binary = true, fileExtensions = List("ccxml"))

    lazy val cdaplusxml: MediaType =
      MediaType("application", "cda+xml", compressible = true, binary = true)

    lazy val cdfxplusxml: MediaType =
      MediaType("application", "cdfx+xml", compressible = true, binary = true, fileExtensions = List("cdfx"))

    lazy val cdmiCapability: MediaType =
      MediaType("application", "cdmi-capability", compressible = false, binary = true, fileExtensions = List("cdmia"))

    lazy val cdmiContainer: MediaType =
      MediaType("application", "cdmi-container", compressible = false, binary = true, fileExtensions = List("cdmic"))

    lazy val cdmiDomain: MediaType =
      MediaType("application", "cdmi-domain", compressible = false, binary = true, fileExtensions = List("cdmid"))

    lazy val cdmiObject: MediaType =
      MediaType("application", "cdmi-object", compressible = false, binary = true, fileExtensions = List("cdmio"))

    lazy val cdmiQueue: MediaType =
      MediaType("application", "cdmi-queue", compressible = false, binary = true, fileExtensions = List("cdmiq"))

    lazy val cdni: MediaType =
      MediaType("application", "cdni", compressible = false, binary = true)

    lazy val cepluscbor: MediaType =
      MediaType("application", "ce+cbor", compressible = false, binary = true)

    lazy val cea: MediaType =
      MediaType("application", "cea", compressible = false, binary = true)

    lazy val cea2018plusxml: MediaType =
      MediaType("application", "cea-2018+xml", compressible = true, binary = true)

    lazy val cellmlplusxml: MediaType =
      MediaType("application", "cellml+xml", compressible = true, binary = true)

    lazy val cfw: MediaType =
      MediaType("application", "cfw", compressible = false, binary = true)

    lazy val cid: MediaType =
      MediaType("application", "cid", compressible = false, binary = true)

    lazy val cidEdhocpluscborSeq: MediaType =
      MediaType("application", "cid-edhoc+cbor-seq", compressible = false, binary = true)

    lazy val cityplusjson: MediaType =
      MediaType("application", "city+json", compressible = true, binary = false)

    lazy val cityplusjsonSeq: MediaType =
      MediaType("application", "city+json-seq", compressible = false, binary = true)

    lazy val clr: MediaType =
      MediaType("application", "clr", compressible = false, binary = true)

    lazy val clueplusxml: MediaType =
      MediaType("application", "clue+xml", compressible = true, binary = true)

    lazy val clueInfoplusxml: MediaType =
      MediaType("application", "clue_info+xml", compressible = true, binary = true)

    lazy val cms: MediaType =
      MediaType("application", "cms", compressible = false, binary = true)

    lazy val cmwpluscbor: MediaType =
      MediaType("application", "cmw+cbor", compressible = false, binary = true)

    lazy val cmwpluscose: MediaType =
      MediaType("application", "cmw+cose", compressible = false, binary = true)

    lazy val cmwplusjson: MediaType =
      MediaType("application", "cmw+json", compressible = true, binary = false)

    lazy val cmwplusjws: MediaType =
      MediaType("application", "cmw+jws", compressible = false, binary = true)

    lazy val cnrpplusxml: MediaType =
      MediaType("application", "cnrp+xml", compressible = true, binary = true)

    lazy val coapEap: MediaType =
      MediaType("application", "coap-eap", compressible = false, binary = true)

    lazy val coapGroupplusjson: MediaType =
      MediaType("application", "coap-group+json", compressible = true, binary = false)

    lazy val coapPayload: MediaType =
      MediaType("application", "coap-payload", compressible = false, binary = true)

    lazy val commonground: MediaType =
      MediaType("application", "commonground", compressible = false, binary = true)

    lazy val conciseProblemDetailspluscbor: MediaType =
      MediaType("application", "concise-problem-details+cbor", compressible = false, binary = true)

    lazy val conferenceInfoplusxml: MediaType =
      MediaType("application", "conference-info+xml", compressible = true, binary = true)

    lazy val cose: MediaType =
      MediaType("application", "cose", compressible = false, binary = true)

    lazy val coseKey: MediaType =
      MediaType("application", "cose-key", compressible = false, binary = true)

    lazy val coseKeySet: MediaType =
      MediaType("application", "cose-key-set", compressible = false, binary = true)

    lazy val coseX509: MediaType =
      MediaType("application", "cose-x509", compressible = false, binary = true)

    lazy val cplplusxml: MediaType =
      MediaType("application", "cpl+xml", compressible = true, binary = true, fileExtensions = List("cpl"))

    lazy val csrattrs: MediaType =
      MediaType("application", "csrattrs", compressible = false, binary = true)

    lazy val cstaplusxml: MediaType =
      MediaType("application", "csta+xml", compressible = true, binary = true)

    lazy val cstadataplusxml: MediaType =
      MediaType("application", "cstadata+xml", compressible = true, binary = true)

    lazy val csvmplusjson: MediaType =
      MediaType("application", "csvm+json", compressible = true, binary = false)

    lazy val cuSeeme: MediaType =
      MediaType("application", "cu-seeme", compressible = false, binary = true, fileExtensions = List("cu"))

    lazy val cwl: MediaType =
      MediaType("application", "cwl", compressible = false, binary = true, fileExtensions = List("cwl"))

    lazy val cwlplusjson: MediaType =
      MediaType("application", "cwl+json", compressible = true, binary = false)

    lazy val cwlplusyaml: MediaType =
      MediaType("application", "cwl+yaml", compressible = false, binary = true)

    lazy val cwt: MediaType =
      MediaType("application", "cwt", compressible = false, binary = true)

    lazy val cybercash: MediaType =
      MediaType("application", "cybercash", compressible = false, binary = true)

    lazy val dart: MediaType =
      MediaType("application", "dart", compressible = true, binary = true)

    lazy val dashplusxml: MediaType =
      MediaType("application", "dash+xml", compressible = true, binary = true, fileExtensions = List("mpd"))

    lazy val dashPatchplusxml: MediaType =
      MediaType("application", "dash-patch+xml", compressible = true, binary = true, fileExtensions = List("mpp"))

    lazy val dashdelta: MediaType =
      MediaType("application", "dashdelta", compressible = false, binary = true)

    lazy val davmountplusxml: MediaType =
      MediaType("application", "davmount+xml", compressible = true, binary = true, fileExtensions = List("davmount"))

    lazy val dcaRft: MediaType =
      MediaType("application", "dca-rft", compressible = false, binary = true)

    lazy val dcd: MediaType =
      MediaType("application", "dcd", compressible = false, binary = true)

    lazy val decDx: MediaType =
      MediaType("application", "dec-dx", compressible = false, binary = true)

    lazy val dialogInfoplusxml: MediaType =
      MediaType("application", "dialog-info+xml", compressible = true, binary = true)

    lazy val dicom: MediaType =
      MediaType("application", "dicom", compressible = false, binary = true, fileExtensions = List("dcm"))

    lazy val dicomplusjson: MediaType =
      MediaType("application", "dicom+json", compressible = true, binary = false)

    lazy val dicomplusxml: MediaType =
      MediaType("application", "dicom+xml", compressible = true, binary = true)

    lazy val did: MediaType =
      MediaType("application", "did", compressible = false, binary = true)

    lazy val dii: MediaType =
      MediaType("application", "dii", compressible = false, binary = true)

    lazy val dit: MediaType =
      MediaType("application", "dit", compressible = false, binary = true)

    lazy val dns: MediaType =
      MediaType("application", "dns", compressible = false, binary = true)

    lazy val dnsplusjson: MediaType =
      MediaType("application", "dns+json", compressible = true, binary = false)

    lazy val dnsMessage: MediaType =
      MediaType("application", "dns-message", compressible = false, binary = true)

    lazy val docbookplusxml: MediaType =
      MediaType("application", "docbook+xml", compressible = true, binary = true, fileExtensions = List("dbk"))

    lazy val dotspluscbor: MediaType =
      MediaType("application", "dots+cbor", compressible = false, binary = true)

    lazy val dpopplusjwt: MediaType =
      MediaType("application", "dpop+jwt", compressible = false, binary = true)

    lazy val dskppplusxml: MediaType =
      MediaType("application", "dskpp+xml", compressible = true, binary = true)

    lazy val dsscplusder: MediaType =
      MediaType("application", "dssc+der", compressible = false, binary = true, fileExtensions = List("dssc"))

    lazy val dsscplusxml: MediaType =
      MediaType("application", "dssc+xml", compressible = true, binary = true, fileExtensions = List("xdssc"))

    lazy val dvcs: MediaType =
      MediaType("application", "dvcs", compressible = false, binary = true)

    lazy val eatpluscwt: MediaType =
      MediaType("application", "eat+cwt", compressible = false, binary = true)

    lazy val eatplusjwt: MediaType =
      MediaType("application", "eat+jwt", compressible = false, binary = true)

    lazy val eatBunpluscbor: MediaType =
      MediaType("application", "eat-bun+cbor", compressible = false, binary = true)

    lazy val eatBunplusjson: MediaType =
      MediaType("application", "eat-bun+json", compressible = true, binary = false)

    lazy val eatUcspluscbor: MediaType =
      MediaType("application", "eat-ucs+cbor", compressible = false, binary = true)

    lazy val eatUcsplusjson: MediaType =
      MediaType("application", "eat-ucs+json", compressible = true, binary = false)

    lazy val ecmascript: MediaType =
      MediaType("application", "ecmascript", compressible = true, binary = true, fileExtensions = List("ecma"))

    lazy val edhocpluscborSeq: MediaType =
      MediaType("application", "edhoc+cbor-seq", compressible = false, binary = true)

    lazy val ediConsent: MediaType =
      MediaType("application", "edi-consent", compressible = false, binary = true)

    lazy val ediX12: MediaType =
      MediaType("application", "edi-x12", compressible = false, binary = true)

    lazy val edifact: MediaType =
      MediaType("application", "edifact", compressible = false, binary = true)

    lazy val efi: MediaType =
      MediaType("application", "efi", compressible = false, binary = true)

    lazy val elmplusjson: MediaType =
      MediaType("application", "elm+json", compressible = true, binary = false)

    lazy val elmplusxml: MediaType =
      MediaType("application", "elm+xml", compressible = true, binary = true)

    lazy val emergencycalldatadotcapplusxml: MediaType =
      MediaType("application", "emergencycalldata.cap+xml", compressible = true, binary = true)

    lazy val emergencycalldatadotcommentplusxml: MediaType =
      MediaType("application", "emergencycalldata.comment+xml", compressible = true, binary = true)

    lazy val emergencycalldatadotcontrolplusxml: MediaType =
      MediaType("application", "emergencycalldata.control+xml", compressible = true, binary = true)

    lazy val emergencycalldatadotdeviceinfoplusxml: MediaType =
      MediaType("application", "emergencycalldata.deviceinfo+xml", compressible = true, binary = true)

    lazy val emergencycalldatadotecalldotmsd: MediaType =
      MediaType("application", "emergencycalldata.ecall.msd", compressible = false, binary = true)

    lazy val emergencycalldatadotlegacyesnplusjson: MediaType =
      MediaType("application", "emergencycalldata.legacyesn+json", compressible = true, binary = false)

    lazy val emergencycalldatadotproviderinfoplusxml: MediaType =
      MediaType("application", "emergencycalldata.providerinfo+xml", compressible = true, binary = true)

    lazy val emergencycalldatadotserviceinfoplusxml: MediaType =
      MediaType("application", "emergencycalldata.serviceinfo+xml", compressible = true, binary = true)

    lazy val emergencycalldatadotsubscriberinfoplusxml: MediaType =
      MediaType("application", "emergencycalldata.subscriberinfo+xml", compressible = true, binary = true)

    lazy val emergencycalldatadotvedsplusxml: MediaType =
      MediaType("application", "emergencycalldata.veds+xml", compressible = true, binary = true)

    lazy val emmaplusxml: MediaType =
      MediaType("application", "emma+xml", compressible = true, binary = true, fileExtensions = List("emma"))

    lazy val emotionmlplusxml: MediaType =
      MediaType("application", "emotionml+xml", compressible = true, binary = true, fileExtensions = List("emotionml"))

    lazy val encaprtp: MediaType =
      MediaType("application", "encaprtp", compressible = false, binary = true)

    lazy val entityStatementplusjwt: MediaType =
      MediaType("application", "entity-statement+jwt", compressible = false, binary = true)

    lazy val eppplusxml: MediaType =
      MediaType("application", "epp+xml", compressible = true, binary = true)

    lazy val epubpluszip: MediaType =
      MediaType("application", "epub+zip", compressible = false, binary = true, fileExtensions = List("epub"))

    lazy val eshop: MediaType =
      MediaType("application", "eshop", compressible = false, binary = true)

    lazy val exi: MediaType =
      MediaType("application", "exi", compressible = false, binary = true, fileExtensions = List("exi"))

    lazy val expectCtReportplusjson: MediaType =
      MediaType("application", "expect-ct-report+json", compressible = true, binary = false)

    lazy val express: MediaType =
      MediaType("application", "express", compressible = false, binary = true, fileExtensions = List("exp"))

    lazy val fastinfoset: MediaType =
      MediaType("application", "fastinfoset", compressible = false, binary = true)

    lazy val fastsoap: MediaType =
      MediaType("application", "fastsoap", compressible = false, binary = true)

    lazy val fdf: MediaType =
      MediaType("application", "fdf", compressible = false, binary = true, fileExtensions = List("fdf"))

    lazy val fdtplusxml: MediaType =
      MediaType("application", "fdt+xml", compressible = true, binary = true, fileExtensions = List("fdt"))

    lazy val fhirplusjson: MediaType =
      MediaType("application", "fhir+json", compressible = true, binary = false)

    lazy val fhirplusxml: MediaType =
      MediaType("application", "fhir+xml", compressible = true, binary = true)

    lazy val fidodottrustedAppsplusjson: MediaType =
      MediaType("application", "fido.trusted-apps+json", compressible = true, binary = false)

    lazy val fits: MediaType =
      MediaType("application", "fits", compressible = false, binary = true)

    lazy val flexfec: MediaType =
      MediaType("application", "flexfec", compressible = false, binary = true)

    lazy val fontSfnt: MediaType =
      MediaType("application", "font-sfnt", compressible = false, binary = true)

    lazy val fontTdpfr: MediaType =
      MediaType("application", "font-tdpfr", compressible = false, binary = true, fileExtensions = List("pfr"))

    lazy val fontWoff: MediaType =
      MediaType("application", "font-woff", compressible = false, binary = true)

    lazy val frameworkAttributesplusxml: MediaType =
      MediaType("application", "framework-attributes+xml", compressible = true, binary = true)

    lazy val geoplusjson: MediaType =
      MediaType("application", "geo+json", compressible = true, binary = false, fileExtensions = List("geojson"))

    lazy val geoplusjsonSeq: MediaType =
      MediaType("application", "geo+json-seq", compressible = false, binary = true)

    lazy val geofeedpluscsv: MediaType =
      MediaType("application", "geofeed+csv", compressible = false, binary = true)

    lazy val geopackageplussqlite3: MediaType =
      MediaType("application", "geopackage+sqlite3", compressible = false, binary = true)

    lazy val geoposeplusjson: MediaType =
      MediaType("application", "geopose+json", compressible = true, binary = false)

    lazy val geoxacmlplusjson: MediaType =
      MediaType("application", "geoxacml+json", compressible = true, binary = false)

    lazy val geoxacmlplusxml: MediaType =
      MediaType("application", "geoxacml+xml", compressible = true, binary = true)

    lazy val gltfBuffer: MediaType =
      MediaType("application", "gltf-buffer", compressible = false, binary = true)

    lazy val gmlplusxml: MediaType =
      MediaType("application", "gml+xml", compressible = true, binary = true, fileExtensions = List("gml"))

    lazy val gnapBindingJws: MediaType =
      MediaType("application", "gnap-binding-jws", compressible = false, binary = true)

    lazy val gnapBindingJwsd: MediaType =
      MediaType("application", "gnap-binding-jwsd", compressible = false, binary = true)

    lazy val gnapBindingRotationJws: MediaType =
      MediaType("application", "gnap-binding-rotation-jws", compressible = false, binary = true)

    lazy val gnapBindingRotationJwsd: MediaType =
      MediaType("application", "gnap-binding-rotation-jwsd", compressible = false, binary = true)

    lazy val gpxplusxml: MediaType =
      MediaType("application", "gpx+xml", compressible = true, binary = true, fileExtensions = List("gpx"))

    lazy val grib: MediaType =
      MediaType("application", "grib", compressible = false, binary = true)

    lazy val gxf: MediaType =
      MediaType("application", "gxf", compressible = false, binary = true, fileExtensions = List("gxf"))

    lazy val gzip: MediaType =
      MediaType("application", "gzip", compressible = false, binary = true, fileExtensions = List("gz"))

    lazy val h224: MediaType =
      MediaType("application", "h224", compressible = false, binary = true)

    lazy val heldplusxml: MediaType =
      MediaType("application", "held+xml", compressible = true, binary = true)

    lazy val hjson: MediaType =
      MediaType("application", "hjson", compressible = false, binary = false, fileExtensions = List("hjson"))

    lazy val hl7v2plusxml: MediaType =
      MediaType("application", "hl7v2+xml", compressible = true, binary = true)

    lazy val http: MediaType =
      MediaType("application", "http", compressible = false, binary = true)

    lazy val hyperstudio: MediaType =
      MediaType("application", "hyperstudio", compressible = false, binary = true, fileExtensions = List("stk"))

    lazy val ibeKeyRequestplusxml: MediaType =
      MediaType("application", "ibe-key-request+xml", compressible = true, binary = true)

    lazy val ibePkgReplyplusxml: MediaType =
      MediaType("application", "ibe-pkg-reply+xml", compressible = true, binary = true)

    lazy val ibePpData: MediaType =
      MediaType("application", "ibe-pp-data", compressible = false, binary = true)

    lazy val iges: MediaType =
      MediaType("application", "iges", compressible = false, binary = true)

    lazy val imIscomposingplusxml: MediaType =
      MediaType("application", "im-iscomposing+xml", compressible = true, binary = true)

    lazy val index: MediaType =
      MediaType("application", "index", compressible = false, binary = true)

    lazy val indexdotcmd: MediaType =
      MediaType("application", "index.cmd", compressible = false, binary = true)

    lazy val indexdotobj: MediaType =
      MediaType("application", "index.obj", compressible = false, binary = true)

    lazy val indexdotresponse: MediaType =
      MediaType("application", "index.response", compressible = false, binary = true)

    lazy val indexdotvnd: MediaType =
      MediaType("application", "index.vnd", compressible = false, binary = true)

    lazy val inkmlplusxml: MediaType =
      MediaType("application", "inkml+xml", compressible = true, binary = true, fileExtensions = List("ink", "inkml"))

    lazy val iotp: MediaType =
      MediaType("application", "iotp", compressible = false, binary = true)

    lazy val ipfix: MediaType =
      MediaType("application", "ipfix", compressible = false, binary = true, fileExtensions = List("ipfix"))

    lazy val ipp: MediaType =
      MediaType("application", "ipp", compressible = false, binary = true)

    lazy val isup: MediaType =
      MediaType("application", "isup", compressible = false, binary = true)

    lazy val itsplusxml: MediaType =
      MediaType("application", "its+xml", compressible = true, binary = true, fileExtensions = List("its"))

    lazy val javaArchive: MediaType =
      MediaType(
        "application",
        "java-archive",
        compressible = false,
        binary = true,
        fileExtensions = List("jar", "war", "ear")
      )

    lazy val javaSerializedObject: MediaType =
      MediaType(
        "application",
        "java-serialized-object",
        compressible = false,
        binary = true,
        fileExtensions = List("ser")
      )

    lazy val javaVm: MediaType =
      MediaType("application", "java-vm", compressible = false, binary = true, fileExtensions = List("class"))

    lazy val javascript: MediaType =
      MediaType("application", "javascript", compressible = true, binary = false, fileExtensions = List("js"))

    lazy val jf2feedplusjson: MediaType =
      MediaType("application", "jf2feed+json", compressible = true, binary = false)

    lazy val jose: MediaType =
      MediaType("application", "jose", compressible = false, binary = true)

    lazy val joseplusjson: MediaType =
      MediaType("application", "jose+json", compressible = true, binary = false)

    lazy val jrdplusjson: MediaType =
      MediaType("application", "jrd+json", compressible = true, binary = false)

    lazy val jscalendarplusjson: MediaType =
      MediaType("application", "jscalendar+json", compressible = true, binary = false)

    lazy val jscontactplusjson: MediaType =
      MediaType("application", "jscontact+json", compressible = true, binary = false)

    lazy val json: MediaType =
      MediaType("application", "json", compressible = true, binary = false, fileExtensions = List("json", "map"))

    lazy val jsonPatchplusjson: MediaType =
      MediaType("application", "json-patch+json", compressible = true, binary = false)

    lazy val jsonPatchQueryplusjson: MediaType =
      MediaType("application", "json-patch-query+json", compressible = true, binary = false)

    lazy val jsonSeq: MediaType =
      MediaType("application", "json-seq", compressible = false, binary = true)

    lazy val json5: MediaType =
      MediaType("application", "json5", compressible = false, binary = true, fileExtensions = List("json5"))

    lazy val jsonmlplusjson: MediaType =
      MediaType("application", "jsonml+json", compressible = true, binary = false, fileExtensions = List("jsonml"))

    lazy val jsonpath: MediaType =
      MediaType("application", "jsonpath", compressible = false, binary = true)

    lazy val jwkplusjson: MediaType =
      MediaType("application", "jwk+json", compressible = true, binary = false)

    lazy val jwkSetplusjson: MediaType =
      MediaType("application", "jwk-set+json", compressible = true, binary = false)

    lazy val jwkSetplusjwt: MediaType =
      MediaType("application", "jwk-set+jwt", compressible = false, binary = true)

    lazy val jwt: MediaType =
      MediaType("application", "jwt", compressible = false, binary = true)

    lazy val kbplusjwt: MediaType =
      MediaType("application", "kb+jwt", compressible = false, binary = true)

    lazy val kblplusxml: MediaType =
      MediaType("application", "kbl+xml", compressible = true, binary = true, fileExtensions = List("kbl"))

    lazy val kpmlRequestplusxml: MediaType =
      MediaType("application", "kpml-request+xml", compressible = true, binary = true)

    lazy val kpmlResponseplusxml: MediaType =
      MediaType("application", "kpml-response+xml", compressible = true, binary = true)

    lazy val ldplusjson: MediaType =
      MediaType("application", "ld+json", compressible = true, binary = false, fileExtensions = List("jsonld"))

    lazy val lgrplusxml: MediaType =
      MediaType("application", "lgr+xml", compressible = true, binary = true, fileExtensions = List("lgr"))

    lazy val linkFormat: MediaType =
      MediaType("application", "link-format", compressible = false, binary = true)

    lazy val linkset: MediaType =
      MediaType("application", "linkset", compressible = false, binary = true)

    lazy val linksetplusjson: MediaType =
      MediaType("application", "linkset+json", compressible = true, binary = false)

    lazy val loadControlplusxml: MediaType =
      MediaType("application", "load-control+xml", compressible = true, binary = true)

    lazy val logoutplusjwt: MediaType =
      MediaType("application", "logout+jwt", compressible = false, binary = true)

    lazy val lostplusxml: MediaType =
      MediaType("application", "lost+xml", compressible = true, binary = true, fileExtensions = List("lostxml"))

    lazy val lostsyncplusxml: MediaType =
      MediaType("application", "lostsync+xml", compressible = true, binary = true)

    lazy val lpfpluszip: MediaType =
      MediaType("application", "lpf+zip", compressible = false, binary = true)

    lazy val lxf: MediaType =
      MediaType("application", "lxf", compressible = false, binary = true)

    lazy val macBinhex40: MediaType =
      MediaType("application", "mac-binhex40", compressible = false, binary = true, fileExtensions = List("hqx"))

    lazy val macCompactpro: MediaType =
      MediaType("application", "mac-compactpro", compressible = false, binary = true, fileExtensions = List("cpt"))

    lazy val macwriteii: MediaType =
      MediaType("application", "macwriteii", compressible = false, binary = true)

    lazy val madsplusxml: MediaType =
      MediaType("application", "mads+xml", compressible = true, binary = true, fileExtensions = List("mads"))

    lazy val manifestplusjson: MediaType =
      MediaType(
        "application",
        "manifest+json",
        compressible = true,
        binary = false,
        fileExtensions = List("webmanifest")
      )

    lazy val marc: MediaType =
      MediaType("application", "marc", compressible = false, binary = true, fileExtensions = List("mrc"))

    lazy val marcxmlplusxml: MediaType =
      MediaType("application", "marcxml+xml", compressible = true, binary = true, fileExtensions = List("mrcx"))

    lazy val mathematica: MediaType =
      MediaType(
        "application",
        "mathematica",
        compressible = false,
        binary = true,
        fileExtensions = List("ma", "nb", "mb")
      )

    lazy val mathmlplusxml: MediaType =
      MediaType("application", "mathml+xml", compressible = true, binary = true, fileExtensions = List("mathml"))

    lazy val mathmlContentplusxml: MediaType =
      MediaType("application", "mathml-content+xml", compressible = true, binary = true)

    lazy val mathmlPresentationplusxml: MediaType =
      MediaType("application", "mathml-presentation+xml", compressible = true, binary = true)

    lazy val mbmsAssociatedProcedureDescriptionplusxml: MediaType =
      MediaType("application", "mbms-associated-procedure-description+xml", compressible = true, binary = true)

    lazy val mbmsDeregisterplusxml: MediaType =
      MediaType("application", "mbms-deregister+xml", compressible = true, binary = true)

    lazy val mbmsEnvelopeplusxml: MediaType =
      MediaType("application", "mbms-envelope+xml", compressible = true, binary = true)

    lazy val mbmsMskplusxml: MediaType =
      MediaType("application", "mbms-msk+xml", compressible = true, binary = true)

    lazy val mbmsMskResponseplusxml: MediaType =
      MediaType("application", "mbms-msk-response+xml", compressible = true, binary = true)

    lazy val mbmsProtectionDescriptionplusxml: MediaType =
      MediaType("application", "mbms-protection-description+xml", compressible = true, binary = true)

    lazy val mbmsReceptionReportplusxml: MediaType =
      MediaType("application", "mbms-reception-report+xml", compressible = true, binary = true)

    lazy val mbmsRegisterplusxml: MediaType =
      MediaType("application", "mbms-register+xml", compressible = true, binary = true)

    lazy val mbmsRegisterResponseplusxml: MediaType =
      MediaType("application", "mbms-register-response+xml", compressible = true, binary = true)

    lazy val mbmsScheduleplusxml: MediaType =
      MediaType("application", "mbms-schedule+xml", compressible = true, binary = true)

    lazy val mbmsUserServiceDescriptionplusxml: MediaType =
      MediaType("application", "mbms-user-service-description+xml", compressible = true, binary = true)

    lazy val mbox: MediaType =
      MediaType("application", "mbox", compressible = false, binary = true, fileExtensions = List("mbox"))

    lazy val mediaPolicyDatasetplusxml: MediaType =
      MediaType(
        "application",
        "media-policy-dataset+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("mpf")
      )

    lazy val mediaControlplusxml: MediaType =
      MediaType("application", "media_control+xml", compressible = true, binary = true)

    lazy val mediaservercontrolplusxml: MediaType =
      MediaType(
        "application",
        "mediaservercontrol+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("mscml")
      )

    lazy val mergePatchplusjson: MediaType =
      MediaType("application", "merge-patch+json", compressible = true, binary = false)

    lazy val metalinkplusxml: MediaType =
      MediaType("application", "metalink+xml", compressible = true, binary = true, fileExtensions = List("metalink"))

    lazy val metalink4plusxml: MediaType =
      MediaType("application", "metalink4+xml", compressible = true, binary = true, fileExtensions = List("meta4"))

    lazy val metsplusxml: MediaType =
      MediaType("application", "mets+xml", compressible = true, binary = true, fileExtensions = List("mets"))

    lazy val mf4: MediaType =
      MediaType("application", "mf4", compressible = false, binary = true)

    lazy val mikey: MediaType =
      MediaType("application", "mikey", compressible = false, binary = true)

    lazy val mipc: MediaType =
      MediaType("application", "mipc", compressible = false, binary = true)

    lazy val missingBlockspluscborSeq: MediaType =
      MediaType("application", "missing-blocks+cbor-seq", compressible = false, binary = true)

    lazy val mmtAeiplusxml: MediaType =
      MediaType("application", "mmt-aei+xml", compressible = true, binary = true, fileExtensions = List("maei"))

    lazy val mmtUsdplusxml: MediaType =
      MediaType("application", "mmt-usd+xml", compressible = true, binary = true, fileExtensions = List("musd"))

    lazy val modsplusxml: MediaType =
      MediaType("application", "mods+xml", compressible = true, binary = true, fileExtensions = List("mods"))

    lazy val mossKeys: MediaType =
      MediaType("application", "moss-keys", compressible = false, binary = true)

    lazy val mossSignature: MediaType =
      MediaType("application", "moss-signature", compressible = false, binary = true)

    lazy val mosskeyData: MediaType =
      MediaType("application", "mosskey-data", compressible = false, binary = true)

    lazy val mosskeyRequest: MediaType =
      MediaType("application", "mosskey-request", compressible = false, binary = true)

    lazy val mp21: MediaType =
      MediaType("application", "mp21", compressible = false, binary = true, fileExtensions = List("m21", "mp21"))

    lazy val mp4: MediaType =
      MediaType(
        "application",
        "mp4",
        compressible = false,
        binary = true,
        fileExtensions = List("mp4", "mpg4", "mp4s", "m4p")
      )

    lazy val mpeg4Generic: MediaType =
      MediaType("application", "mpeg4-generic", compressible = false, binary = true)

    lazy val mpeg4Iod: MediaType =
      MediaType("application", "mpeg4-iod", compressible = false, binary = true)

    lazy val mpeg4IodXmt: MediaType =
      MediaType("application", "mpeg4-iod-xmt", compressible = false, binary = true)

    lazy val mrbConsumerplusxml: MediaType =
      MediaType("application", "mrb-consumer+xml", compressible = true, binary = true)

    lazy val mrbPublishplusxml: MediaType =
      MediaType("application", "mrb-publish+xml", compressible = true, binary = true)

    lazy val mscIvrplusxml: MediaType =
      MediaType("application", "msc-ivr+xml", compressible = true, binary = true)

    lazy val mscMixerplusxml: MediaType =
      MediaType("application", "msc-mixer+xml", compressible = true, binary = true)

    lazy val msix: MediaType =
      MediaType("application", "msix", compressible = false, binary = true, fileExtensions = List("msix"))

    lazy val msixbundle: MediaType =
      MediaType("application", "msixbundle", compressible = false, binary = true, fileExtensions = List("msixbundle"))

    lazy val msword: MediaType =
      MediaType("application", "msword", compressible = false, binary = true, fileExtensions = List("doc", "dot"))

    lazy val mudplusjson: MediaType =
      MediaType("application", "mud+json", compressible = true, binary = false)

    lazy val multipartCore: MediaType =
      MediaType("application", "multipart-core", compressible = false, binary = true)

    lazy val mxf: MediaType =
      MediaType("application", "mxf", compressible = false, binary = true, fileExtensions = List("mxf"))

    lazy val nQuads: MediaType =
      MediaType("application", "n-quads", compressible = false, binary = true, fileExtensions = List("nq"))

    lazy val nTriples: MediaType =
      MediaType("application", "n-triples", compressible = false, binary = true, fileExtensions = List("nt"))

    lazy val nasdata: MediaType =
      MediaType("application", "nasdata", compressible = false, binary = true)

    lazy val newsCheckgroups: MediaType =
      MediaType("application", "news-checkgroups", compressible = false, binary = true)

    lazy val newsGroupinfo: MediaType =
      MediaType("application", "news-groupinfo", compressible = false, binary = true)

    lazy val newsTransmission: MediaType =
      MediaType("application", "news-transmission", compressible = false, binary = true)

    lazy val nlsmlplusxml: MediaType =
      MediaType("application", "nlsml+xml", compressible = true, binary = true)

    lazy val node: MediaType =
      MediaType("application", "node", compressible = false, binary = true, fileExtensions = List("cjs"))

    lazy val nss: MediaType =
      MediaType("application", "nss", compressible = false, binary = true)

    lazy val oauthAuthzReqplusjwt: MediaType =
      MediaType("application", "oauth-authz-req+jwt", compressible = false, binary = true)

    lazy val obliviousDnsMessage: MediaType =
      MediaType("application", "oblivious-dns-message", compressible = false, binary = true)

    lazy val ocspRequest: MediaType =
      MediaType("application", "ocsp-request", compressible = false, binary = true)

    lazy val ocspResponse: MediaType =
      MediaType("application", "ocsp-response", compressible = false, binary = true)

    lazy val octetStream: MediaType =
      MediaType(
        "application",
        "octet-stream",
        compressible = true,
        binary = true,
        fileExtensions = List(
          "bin",
          "dms",
          "lrf",
          "mar",
          "so",
          "dist",
          "distz",
          "pkg",
          "bpk",
          "dump",
          "elc",
          "deploy",
          "exe",
          "dll",
          "deb",
          "dmg",
          "iso",
          "img",
          "msi",
          "msp",
          "msm",
          "buffer"
        )
      )

    lazy val oda: MediaType =
      MediaType("application", "oda", compressible = false, binary = true, fileExtensions = List("oda"))

    lazy val odmplusxml: MediaType =
      MediaType("application", "odm+xml", compressible = true, binary = true)

    lazy val odx: MediaType =
      MediaType("application", "odx", compressible = false, binary = true)

    lazy val oebpsPackageplusxml: MediaType =
      MediaType("application", "oebps-package+xml", compressible = true, binary = true, fileExtensions = List("opf"))

    lazy val ogg: MediaType =
      MediaType("application", "ogg", compressible = false, binary = true, fileExtensions = List("ogx"))

    lazy val ohttpKeys: MediaType =
      MediaType("application", "ohttp-keys", compressible = false, binary = true)

    lazy val omdocplusxml: MediaType =
      MediaType("application", "omdoc+xml", compressible = true, binary = true, fileExtensions = List("omdoc"))

    lazy val onenote: MediaType =
      MediaType(
        "application",
        "onenote",
        compressible = false,
        binary = true,
        fileExtensions = List("onetoc", "onetoc2", "onetmp", "onepkg", "one", "onea")
      )

    lazy val opcNodesetplusxml: MediaType =
      MediaType("application", "opc-nodeset+xml", compressible = true, binary = true)

    lazy val oscore: MediaType =
      MediaType("application", "oscore", compressible = false, binary = true)

    lazy val oxps: MediaType =
      MediaType("application", "oxps", compressible = false, binary = true, fileExtensions = List("oxps"))

    lazy val p21: MediaType =
      MediaType("application", "p21", compressible = false, binary = true)

    lazy val p21pluszip: MediaType =
      MediaType("application", "p21+zip", compressible = false, binary = true)

    lazy val p2pOverlayplusxml: MediaType =
      MediaType("application", "p2p-overlay+xml", compressible = true, binary = true, fileExtensions = List("relo"))

    lazy val parityfec: MediaType =
      MediaType("application", "parityfec", compressible = false, binary = true)

    lazy val passport: MediaType =
      MediaType("application", "passport", compressible = false, binary = true)

    lazy val patchOpsErrorplusxml: MediaType =
      MediaType("application", "patch-ops-error+xml", compressible = true, binary = true, fileExtensions = List("xer"))

    lazy val pdf: MediaType =
      MediaType("application", "pdf", compressible = false, binary = true, fileExtensions = List("pdf"))

    lazy val pdx: MediaType =
      MediaType("application", "pdx", compressible = false, binary = true)

    lazy val pemCertificateChain: MediaType =
      MediaType("application", "pem-certificate-chain", compressible = false, binary = true)

    lazy val pgpEncrypted: MediaType =
      MediaType("application", "pgp-encrypted", compressible = false, binary = true, fileExtensions = List("pgp"))

    lazy val pgpKeys: MediaType =
      MediaType("application", "pgp-keys", compressible = false, binary = true, fileExtensions = List("asc"))

    lazy val pgpSignature: MediaType =
      MediaType(
        "application",
        "pgp-signature",
        compressible = false,
        binary = true,
        fileExtensions = List("sig", "asc")
      )

    lazy val picsRules: MediaType =
      MediaType("application", "pics-rules", compressible = false, binary = true, fileExtensions = List("prf"))

    lazy val pidfplusxml: MediaType =
      MediaType("application", "pidf+xml", compressible = true, binary = true)

    lazy val pidfDiffplusxml: MediaType =
      MediaType("application", "pidf-diff+xml", compressible = true, binary = true)

    lazy val pkcs10: MediaType =
      MediaType("application", "pkcs10", compressible = false, binary = true, fileExtensions = List("p10"))

    lazy val pkcs12: MediaType =
      MediaType("application", "pkcs12", compressible = false, binary = true)

    lazy val pkcs7Mime: MediaType =
      MediaType("application", "pkcs7-mime", compressible = false, binary = true, fileExtensions = List("p7m", "p7c"))

    lazy val pkcs7Signature: MediaType =
      MediaType("application", "pkcs7-signature", compressible = false, binary = true, fileExtensions = List("p7s"))

    lazy val pkcs8: MediaType =
      MediaType("application", "pkcs8", compressible = false, binary = true, fileExtensions = List("p8"))

    lazy val pkcs8Encrypted: MediaType =
      MediaType("application", "pkcs8-encrypted", compressible = false, binary = true)

    lazy val pkixAttrCert: MediaType =
      MediaType("application", "pkix-attr-cert", compressible = false, binary = true, fileExtensions = List("ac"))

    lazy val pkixCert: MediaType =
      MediaType("application", "pkix-cert", compressible = false, binary = true, fileExtensions = List("cer"))

    lazy val pkixCrl: MediaType =
      MediaType("application", "pkix-crl", compressible = false, binary = true, fileExtensions = List("crl"))

    lazy val pkixPkipath: MediaType =
      MediaType("application", "pkix-pkipath", compressible = false, binary = true, fileExtensions = List("pkipath"))

    lazy val pkixcmp: MediaType =
      MediaType("application", "pkixcmp", compressible = false, binary = true, fileExtensions = List("pki"))

    lazy val plsplusxml: MediaType =
      MediaType("application", "pls+xml", compressible = true, binary = true, fileExtensions = List("pls"))

    lazy val pocSettingsplusxml: MediaType =
      MediaType("application", "poc-settings+xml", compressible = true, binary = true)

    lazy val postscript: MediaType =
      MediaType(
        "application",
        "postscript",
        compressible = true,
        binary = true,
        fileExtensions = List("ai", "eps", "ps")
      )

    lazy val ppspTrackerplusjson: MediaType =
      MediaType("application", "ppsp-tracker+json", compressible = true, binary = false)

    lazy val privateTokenIssuerDirectory: MediaType =
      MediaType("application", "private-token-issuer-directory", compressible = false, binary = true)

    lazy val privateTokenRequest: MediaType =
      MediaType("application", "private-token-request", compressible = false, binary = true)

    lazy val privateTokenResponse: MediaType =
      MediaType("application", "private-token-response", compressible = false, binary = true)

    lazy val problemplusjson: MediaType =
      MediaType("application", "problem+json", compressible = true, binary = false)

    lazy val problemplusxml: MediaType =
      MediaType("application", "problem+xml", compressible = true, binary = true)

    lazy val protobuf: MediaType =
      MediaType("application", "protobuf", compressible = false, binary = true)

    lazy val protobufplusjson: MediaType =
      MediaType("application", "protobuf+json", compressible = true, binary = false)

    lazy val provenanceplusxml: MediaType =
      MediaType("application", "provenance+xml", compressible = true, binary = true, fileExtensions = List("provx"))

    lazy val providedClaimsplusjwt: MediaType =
      MediaType("application", "provided-claims+jwt", compressible = false, binary = true)

    lazy val prsdotalvestranddottitraxSheet: MediaType =
      MediaType("application", "prs.alvestrand.titrax-sheet", compressible = false, binary = true)

    lazy val prsdotcww: MediaType =
      MediaType("application", "prs.cww", compressible = false, binary = true, fileExtensions = List("cww"))

    lazy val prsdotcyn: MediaType =
      MediaType("application", "prs.cyn", compressible = false, binary = true)

    lazy val prsdothpubpluszip: MediaType =
      MediaType("application", "prs.hpub+zip", compressible = false, binary = true)

    lazy val prsdotimpliedDocumentplusxml: MediaType =
      MediaType("application", "prs.implied-document+xml", compressible = true, binary = true)

    lazy val prsdotimpliedExecutable: MediaType =
      MediaType("application", "prs.implied-executable", compressible = false, binary = true)

    lazy val prsdotimpliedObjectplusjson: MediaType =
      MediaType("application", "prs.implied-object+json", compressible = true, binary = false)

    lazy val prsdotimpliedObjectplusjsonSeq: MediaType =
      MediaType("application", "prs.implied-object+json-seq", compressible = false, binary = true)

    lazy val prsdotimpliedObjectplusyaml: MediaType =
      MediaType("application", "prs.implied-object+yaml", compressible = false, binary = true)

    lazy val prsdotimpliedStructure: MediaType =
      MediaType("application", "prs.implied-structure", compressible = false, binary = true)

    lazy val prsdotmayfile: MediaType =
      MediaType("application", "prs.mayfile", compressible = false, binary = true)

    lazy val prsdotnprend: MediaType =
      MediaType("application", "prs.nprend", compressible = false, binary = true)

    lazy val prsdotplucker: MediaType =
      MediaType("application", "prs.plucker", compressible = false, binary = true)

    lazy val prsdotrdfXmlCrypt: MediaType =
      MediaType("application", "prs.rdf-xml-crypt", compressible = false, binary = true)

    lazy val prsdotsclt: MediaType =
      MediaType("application", "prs.sclt", compressible = false, binary = true)

    lazy val prsdotvcfbzip2: MediaType =
      MediaType("application", "prs.vcfbzip2", compressible = false, binary = true)

    lazy val prsdotxsfplusxml: MediaType =
      MediaType("application", "prs.xsf+xml", compressible = true, binary = true, fileExtensions = List("xsf"))

    lazy val pskcplusxml: MediaType =
      MediaType("application", "pskc+xml", compressible = true, binary = true, fileExtensions = List("pskcxml"))

    lazy val pvdplusjson: MediaType =
      MediaType("application", "pvd+json", compressible = true, binary = false)

    lazy val qsig: MediaType =
      MediaType("application", "qsig", compressible = false, binary = true)

    lazy val ramlplusyaml: MediaType =
      MediaType("application", "raml+yaml", compressible = true, binary = true, fileExtensions = List("raml"))

    lazy val raptorfec: MediaType =
      MediaType("application", "raptorfec", compressible = false, binary = true)

    lazy val rdapplusjson: MediaType =
      MediaType("application", "rdap+json", compressible = true, binary = false)

    lazy val rdfplusxml: MediaType =
      MediaType("application", "rdf+xml", compressible = true, binary = true, fileExtensions = List("rdf", "owl"))

    lazy val reginfoplusxml: MediaType =
      MediaType("application", "reginfo+xml", compressible = true, binary = true, fileExtensions = List("rif"))

    lazy val relaxNgCompactSyntax: MediaType =
      MediaType(
        "application",
        "relax-ng-compact-syntax",
        compressible = false,
        binary = true,
        fileExtensions = List("rnc")
      )

    lazy val remotePrinting: MediaType =
      MediaType("application", "remote-printing", compressible = false, binary = true)

    lazy val reputonplusjson: MediaType =
      MediaType("application", "reputon+json", compressible = true, binary = false)

    lazy val resolveResponseplusjwt: MediaType =
      MediaType("application", "resolve-response+jwt", compressible = false, binary = true)

    lazy val resourceListsplusxml: MediaType =
      MediaType("application", "resource-lists+xml", compressible = true, binary = true, fileExtensions = List("rl"))

    lazy val resourceListsDiffplusxml: MediaType =
      MediaType(
        "application",
        "resource-lists-diff+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("rld")
      )

    lazy val rfcplusxml: MediaType =
      MediaType("application", "rfc+xml", compressible = true, binary = true)

    lazy val riscos: MediaType =
      MediaType("application", "riscos", compressible = false, binary = true)

    lazy val rlmiplusxml: MediaType =
      MediaType("application", "rlmi+xml", compressible = true, binary = true)

    lazy val rlsServicesplusxml: MediaType =
      MediaType("application", "rls-services+xml", compressible = true, binary = true, fileExtensions = List("rs"))

    lazy val routeApdplusxml: MediaType =
      MediaType("application", "route-apd+xml", compressible = true, binary = true, fileExtensions = List("rapd"))

    lazy val routeSTsidplusxml: MediaType =
      MediaType("application", "route-s-tsid+xml", compressible = true, binary = true, fileExtensions = List("sls"))

    lazy val routeUsdplusxml: MediaType =
      MediaType("application", "route-usd+xml", compressible = true, binary = true, fileExtensions = List("rusd"))

    lazy val rpkiChecklist: MediaType =
      MediaType("application", "rpki-checklist", compressible = false, binary = true)

    lazy val rpkiGhostbusters: MediaType =
      MediaType("application", "rpki-ghostbusters", compressible = false, binary = true, fileExtensions = List("gbr"))

    lazy val rpkiManifest: MediaType =
      MediaType("application", "rpki-manifest", compressible = false, binary = true, fileExtensions = List("mft"))

    lazy val rpkiPublication: MediaType =
      MediaType("application", "rpki-publication", compressible = false, binary = true)

    lazy val rpkiRoa: MediaType =
      MediaType("application", "rpki-roa", compressible = false, binary = true, fileExtensions = List("roa"))

    lazy val rpkiSignedTal: MediaType =
      MediaType("application", "rpki-signed-tal", compressible = false, binary = true)

    lazy val rpkiUpdown: MediaType =
      MediaType("application", "rpki-updown", compressible = false, binary = true)

    lazy val rsMetadataplusxml: MediaType =
      MediaType("application", "rs-metadata+xml", compressible = true, binary = true)

    lazy val rsdplusxml: MediaType =
      MediaType("application", "rsd+xml", compressible = true, binary = true, fileExtensions = List("rsd"))

    lazy val rssplusxml: MediaType =
      MediaType("application", "rss+xml", compressible = true, binary = true, fileExtensions = List("rss"))

    lazy val rtf: MediaType =
      MediaType("application", "rtf", compressible = true, binary = true, fileExtensions = List("rtf"))

    lazy val rtploopback: MediaType =
      MediaType("application", "rtploopback", compressible = false, binary = true)

    lazy val rtx: MediaType =
      MediaType("application", "rtx", compressible = false, binary = true)

    lazy val samlassertionplusxml: MediaType =
      MediaType("application", "samlassertion+xml", compressible = true, binary = true)

    lazy val samlmetadataplusxml: MediaType =
      MediaType("application", "samlmetadata+xml", compressible = true, binary = true)

    lazy val sarifplusjson: MediaType =
      MediaType("application", "sarif+json", compressible = true, binary = false)

    lazy val sarifExternalPropertiesplusjson: MediaType =
      MediaType("application", "sarif-external-properties+json", compressible = true, binary = false)

    lazy val sbe: MediaType =
      MediaType("application", "sbe", compressible = false, binary = true)

    lazy val sbmlplusxml: MediaType =
      MediaType("application", "sbml+xml", compressible = true, binary = true, fileExtensions = List("sbml"))

    lazy val scaipplusxml: MediaType =
      MediaType("application", "scaip+xml", compressible = true, binary = true)

    lazy val scimplusjson: MediaType =
      MediaType("application", "scim+json", compressible = true, binary = false)

    lazy val scittReceiptpluscose: MediaType =
      MediaType("application", "scitt-receipt+cose", compressible = false, binary = true)

    lazy val scittStatementpluscose: MediaType =
      MediaType("application", "scitt-statement+cose", compressible = false, binary = true)

    lazy val scvpCvRequest: MediaType =
      MediaType("application", "scvp-cv-request", compressible = false, binary = true, fileExtensions = List("scq"))

    lazy val scvpCvResponse: MediaType =
      MediaType("application", "scvp-cv-response", compressible = false, binary = true, fileExtensions = List("scs"))

    lazy val scvpVpRequest: MediaType =
      MediaType("application", "scvp-vp-request", compressible = false, binary = true, fileExtensions = List("spq"))

    lazy val scvpVpResponse: MediaType =
      MediaType("application", "scvp-vp-response", compressible = false, binary = true, fileExtensions = List("spp"))

    lazy val sdJwt: MediaType =
      MediaType("application", "sd-jwt", compressible = false, binary = true)

    lazy val sdJwtplusjson: MediaType =
      MediaType("application", "sd-jwt+json", compressible = true, binary = false)

    lazy val sdfplusjson: MediaType =
      MediaType("application", "sdf+json", compressible = true, binary = false)

    lazy val sdp: MediaType =
      MediaType("application", "sdp", compressible = false, binary = true, fileExtensions = List("sdp"))

    lazy val seceventplusjwt: MediaType =
      MediaType("application", "secevent+jwt", compressible = false, binary = true)

    lazy val senmlpluscbor: MediaType =
      MediaType("application", "senml+cbor", compressible = false, binary = true)

    lazy val senmlplusjson: MediaType =
      MediaType("application", "senml+json", compressible = true, binary = false)

    lazy val senmlplusxml: MediaType =
      MediaType("application", "senml+xml", compressible = true, binary = true, fileExtensions = List("senmlx"))

    lazy val senmlEtchpluscbor: MediaType =
      MediaType("application", "senml-etch+cbor", compressible = false, binary = true)

    lazy val senmlEtchplusjson: MediaType =
      MediaType("application", "senml-etch+json", compressible = true, binary = false)

    lazy val senmlExi: MediaType =
      MediaType("application", "senml-exi", compressible = false, binary = true)

    lazy val sensmlpluscbor: MediaType =
      MediaType("application", "sensml+cbor", compressible = false, binary = true)

    lazy val sensmlplusjson: MediaType =
      MediaType("application", "sensml+json", compressible = true, binary = false)

    lazy val sensmlplusxml: MediaType =
      MediaType("application", "sensml+xml", compressible = true, binary = true, fileExtensions = List("sensmlx"))

    lazy val sensmlExi: MediaType =
      MediaType("application", "sensml-exi", compressible = false, binary = true)

    lazy val sepplusxml: MediaType =
      MediaType("application", "sep+xml", compressible = true, binary = true)

    lazy val sepExi: MediaType =
      MediaType("application", "sep-exi", compressible = false, binary = true)

    lazy val sessionInfo: MediaType =
      MediaType("application", "session-info", compressible = false, binary = true)

    lazy val setPayment: MediaType =
      MediaType("application", "set-payment", compressible = false, binary = true)

    lazy val setPaymentInitiation: MediaType =
      MediaType(
        "application",
        "set-payment-initiation",
        compressible = false,
        binary = true,
        fileExtensions = List("setpay")
      )

    lazy val setRegistration: MediaType =
      MediaType("application", "set-registration", compressible = false, binary = true)

    lazy val setRegistrationInitiation: MediaType =
      MediaType(
        "application",
        "set-registration-initiation",
        compressible = false,
        binary = true,
        fileExtensions = List("setreg")
      )

    lazy val sgml: MediaType =
      MediaType("application", "sgml", compressible = false, binary = true)

    lazy val sgmlOpenCatalog: MediaType =
      MediaType("application", "sgml-open-catalog", compressible = false, binary = true)

    lazy val shfplusxml: MediaType =
      MediaType("application", "shf+xml", compressible = true, binary = true, fileExtensions = List("shf"))

    lazy val sieve: MediaType =
      MediaType("application", "sieve", compressible = false, binary = true, fileExtensions = List("siv", "sieve"))

    lazy val simpleFilterplusxml: MediaType =
      MediaType("application", "simple-filter+xml", compressible = true, binary = true)

    lazy val simpleMessageSummary: MediaType =
      MediaType("application", "simple-message-summary", compressible = false, binary = true)

    lazy val simplesymbolcontainer: MediaType =
      MediaType("application", "simplesymbolcontainer", compressible = false, binary = true)

    lazy val sipc: MediaType =
      MediaType("application", "sipc", compressible = false, binary = true)

    lazy val slate: MediaType =
      MediaType("application", "slate", compressible = false, binary = true)

    lazy val smil: MediaType =
      MediaType("application", "smil", compressible = false, binary = true)

    lazy val smilplusxml: MediaType =
      MediaType("application", "smil+xml", compressible = true, binary = true, fileExtensions = List("smi", "smil"))

    lazy val smpte336m: MediaType =
      MediaType("application", "smpte336m", compressible = false, binary = true)

    lazy val soapplusfastinfoset: MediaType =
      MediaType("application", "soap+fastinfoset", compressible = false, binary = true)

    lazy val soapplusxml: MediaType =
      MediaType("application", "soap+xml", compressible = true, binary = true)

    lazy val sparqlQuery: MediaType =
      MediaType("application", "sparql-query", compressible = false, binary = true, fileExtensions = List("rq"))

    lazy val sparqlResultsplusxml: MediaType =
      MediaType("application", "sparql-results+xml", compressible = true, binary = true, fileExtensions = List("srx"))

    lazy val spdxplusjson: MediaType =
      MediaType("application", "spdx+json", compressible = true, binary = false)

    lazy val spiritsEventplusxml: MediaType =
      MediaType("application", "spirits-event+xml", compressible = true, binary = true)

    lazy val sql: MediaType =
      MediaType("application", "sql", compressible = false, binary = true, fileExtensions = List("sql"))

    lazy val srgs: MediaType =
      MediaType("application", "srgs", compressible = false, binary = true, fileExtensions = List("gram"))

    lazy val srgsplusxml: MediaType =
      MediaType("application", "srgs+xml", compressible = true, binary = true, fileExtensions = List("grxml"))

    lazy val sruplusxml: MediaType =
      MediaType("application", "sru+xml", compressible = true, binary = true, fileExtensions = List("sru"))

    lazy val ssdlplusxml: MediaType =
      MediaType("application", "ssdl+xml", compressible = true, binary = true, fileExtensions = List("ssdl"))

    lazy val sslkeylogfile: MediaType =
      MediaType("application", "sslkeylogfile", compressible = false, binary = true)

    lazy val ssmlplusxml: MediaType =
      MediaType("application", "ssml+xml", compressible = true, binary = true, fileExtensions = List("ssml"))

    lazy val st211041: MediaType =
      MediaType("application", "st2110-41", compressible = false, binary = true)

    lazy val stixplusjson: MediaType =
      MediaType("application", "stix+json", compressible = true, binary = false)

    lazy val stratum: MediaType =
      MediaType("application", "stratum", compressible = false, binary = true)

    lazy val suitEnvelopepluscose: MediaType =
      MediaType("application", "suit-envelope+cose", compressible = false, binary = true)

    lazy val suitReportpluscose: MediaType =
      MediaType("application", "suit-report+cose", compressible = false, binary = true)

    lazy val swidpluscbor: MediaType =
      MediaType("application", "swid+cbor", compressible = false, binary = true)

    lazy val swidplusxml: MediaType =
      MediaType("application", "swid+xml", compressible = true, binary = true, fileExtensions = List("swidtag"))

    lazy val tampApexUpdate: MediaType =
      MediaType("application", "tamp-apex-update", compressible = false, binary = true)

    lazy val tampApexUpdateConfirm: MediaType =
      MediaType("application", "tamp-apex-update-confirm", compressible = false, binary = true)

    lazy val tampCommunityUpdate: MediaType =
      MediaType("application", "tamp-community-update", compressible = false, binary = true)

    lazy val tampCommunityUpdateConfirm: MediaType =
      MediaType("application", "tamp-community-update-confirm", compressible = false, binary = true)

    lazy val tampError: MediaType =
      MediaType("application", "tamp-error", compressible = false, binary = true)

    lazy val tampSequenceAdjust: MediaType =
      MediaType("application", "tamp-sequence-adjust", compressible = false, binary = true)

    lazy val tampSequenceAdjustConfirm: MediaType =
      MediaType("application", "tamp-sequence-adjust-confirm", compressible = false, binary = true)

    lazy val tampStatusQuery: MediaType =
      MediaType("application", "tamp-status-query", compressible = false, binary = true)

    lazy val tampStatusResponse: MediaType =
      MediaType("application", "tamp-status-response", compressible = false, binary = true)

    lazy val tampUpdate: MediaType =
      MediaType("application", "tamp-update", compressible = false, binary = true)

    lazy val tampUpdateConfirm: MediaType =
      MediaType("application", "tamp-update-confirm", compressible = false, binary = true)

    lazy val tar: MediaType =
      MediaType("application", "tar", compressible = true, binary = true)

    lazy val taxiiplusjson: MediaType =
      MediaType("application", "taxii+json", compressible = true, binary = false)

    lazy val tdplusjson: MediaType =
      MediaType("application", "td+json", compressible = true, binary = false)

    lazy val teiplusxml: MediaType =
      MediaType("application", "tei+xml", compressible = true, binary = true, fileExtensions = List("tei", "teicorpus"))

    lazy val tetraIsi: MediaType =
      MediaType("application", "tetra_isi", compressible = false, binary = true)

    lazy val texinfo: MediaType =
      MediaType("application", "texinfo", compressible = false, binary = true)

    lazy val thraudplusxml: MediaType =
      MediaType("application", "thraud+xml", compressible = true, binary = true, fileExtensions = List("tfi"))

    lazy val timestampQuery: MediaType =
      MediaType("application", "timestamp-query", compressible = false, binary = true)

    lazy val timestampReply: MediaType =
      MediaType("application", "timestamp-reply", compressible = false, binary = true)

    lazy val timestampedData: MediaType =
      MediaType("application", "timestamped-data", compressible = false, binary = true, fileExtensions = List("tsd"))

    lazy val tlsrptplusgzip: MediaType =
      MediaType("application", "tlsrpt+gzip", compressible = false, binary = true)

    lazy val tlsrptplusjson: MediaType =
      MediaType("application", "tlsrpt+json", compressible = true, binary = false)

    lazy val tmplusjson: MediaType =
      MediaType("application", "tm+json", compressible = true, binary = false)

    lazy val tnauthlist: MediaType =
      MediaType("application", "tnauthlist", compressible = false, binary = true)

    lazy val tocpluscbor: MediaType =
      MediaType("application", "toc+cbor", compressible = false, binary = true)

    lazy val tokenIntrospectionplusjwt: MediaType =
      MediaType("application", "token-introspection+jwt", compressible = false, binary = true)

    lazy val toml: MediaType =
      MediaType("application", "toml", compressible = true, binary = true, fileExtensions = List("toml"))

    lazy val trickleIceSdpfrag: MediaType =
      MediaType("application", "trickle-ice-sdpfrag", compressible = false, binary = true)

    lazy val trig: MediaType =
      MediaType("application", "trig", compressible = false, binary = true, fileExtensions = List("trig"))

    lazy val trustChainplusjson: MediaType =
      MediaType("application", "trust-chain+json", compressible = true, binary = false)

    lazy val trustMarkplusjwt: MediaType =
      MediaType("application", "trust-mark+jwt", compressible = false, binary = true)

    lazy val trustMarkDelegationplusjwt: MediaType =
      MediaType("application", "trust-mark-delegation+jwt", compressible = false, binary = true)

    lazy val ttmlplusxml: MediaType =
      MediaType("application", "ttml+xml", compressible = true, binary = true, fileExtensions = List("ttml"))

    lazy val tveTrigger: MediaType =
      MediaType("application", "tve-trigger", compressible = false, binary = true)

    lazy val tzif: MediaType =
      MediaType("application", "tzif", compressible = false, binary = true)

    lazy val tzifLeap: MediaType =
      MediaType("application", "tzif-leap", compressible = false, binary = true)

    lazy val ubjson: MediaType =
      MediaType("application", "ubjson", compressible = false, binary = false, fileExtensions = List("ubj"))

    lazy val uccspluscbor: MediaType =
      MediaType("application", "uccs+cbor", compressible = false, binary = true)

    lazy val ujcsplusjson: MediaType =
      MediaType("application", "ujcs+json", compressible = true, binary = false)

    lazy val ulpfec: MediaType =
      MediaType("application", "ulpfec", compressible = false, binary = true)

    lazy val urcGrpsheetplusxml: MediaType =
      MediaType("application", "urc-grpsheet+xml", compressible = true, binary = true)

    lazy val urcRessheetplusxml: MediaType =
      MediaType("application", "urc-ressheet+xml", compressible = true, binary = true, fileExtensions = List("rsheet"))

    lazy val urcTargetdescplusxml: MediaType =
      MediaType("application", "urc-targetdesc+xml", compressible = true, binary = true, fileExtensions = List("td"))

    lazy val urcUisocketdescplusxml: MediaType =
      MediaType("application", "urc-uisocketdesc+xml", compressible = true, binary = true)

    lazy val vc: MediaType =
      MediaType("application", "vc", compressible = false, binary = true)

    lazy val vcpluscose: MediaType =
      MediaType("application", "vc+cose", compressible = false, binary = true)

    lazy val vcplusjwt: MediaType =
      MediaType("application", "vc+jwt", compressible = false, binary = true)

    lazy val vcplussdJwt: MediaType =
      MediaType("application", "vc+sd-jwt", compressible = false, binary = true)

    lazy val vcardplusjson: MediaType =
      MediaType("application", "vcard+json", compressible = true, binary = false)

    lazy val vcardplusxml: MediaType =
      MediaType("application", "vcard+xml", compressible = true, binary = true)

    lazy val vecplusxml: MediaType =
      MediaType("application", "vec+xml", compressible = true, binary = true, fileExtensions = List("vec"))

    lazy val vecPackageplusgzip: MediaType =
      MediaType("application", "vec-package+gzip", compressible = false, binary = true)

    lazy val vecPackagepluszip: MediaType =
      MediaType("application", "vec-package+zip", compressible = false, binary = true)

    lazy val vemmi: MediaType =
      MediaType("application", "vemmi", compressible = false, binary = true)

    lazy val vividencedotscriptfile: MediaType =
      MediaType("application", "vividence.scriptfile", compressible = false, binary = true)

    lazy val vnddot1000mindsdotdecisionModelplusxml: MediaType =
      MediaType(
        "application",
        "vnd.1000minds.decision-model+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("1km")
      )

    lazy val vnddot1ob: MediaType =
      MediaType("application", "vnd.1ob", compressible = false, binary = true)

    lazy val vnddot3gppProseplusxml: MediaType =
      MediaType("application", "vnd.3gpp-prose+xml", compressible = true, binary = true)

    lazy val vnddot3gppProsePc3aplusxml: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc3a+xml", compressible = true, binary = true)

    lazy val vnddot3gppProsePc3achplusxml: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc3ach+xml", compressible = true, binary = true)

    lazy val vnddot3gppProsePc3chplusxml: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc3ch+xml", compressible = true, binary = true)

    lazy val vnddot3gppProsePc8plusxml: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc8+xml", compressible = true, binary = true)

    lazy val vnddot3gppV2xLocalServiceInformation: MediaType =
      MediaType("application", "vnd.3gpp-v2x-local-service-information", compressible = false, binary = true)

    lazy val vnddot3gppdot5gnas: MediaType =
      MediaType("application", "vnd.3gpp.5gnas", compressible = false, binary = true)

    lazy val vnddot3gppdot5gsa2x: MediaType =
      MediaType("application", "vnd.3gpp.5gsa2x", compressible = false, binary = true)

    lazy val vnddot3gppdot5gsa2xLocalServiceInformation: MediaType =
      MediaType("application", "vnd.3gpp.5gsa2x-local-service-information", compressible = false, binary = true)

    lazy val vnddot3gppdot5gsv2x: MediaType =
      MediaType("application", "vnd.3gpp.5gsv2x", compressible = false, binary = true)

    lazy val vnddot3gppdot5gsv2xLocalServiceInformation: MediaType =
      MediaType("application", "vnd.3gpp.5gsv2x-local-service-information", compressible = false, binary = true)

    lazy val vnddot3gppdotaccessTransferEventsplusxml: MediaType =
      MediaType("application", "vnd.3gpp.access-transfer-events+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotbsfplusxml: MediaType =
      MediaType("application", "vnd.3gpp.bsf+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotcrsplusxml: MediaType =
      MediaType("application", "vnd.3gpp.crs+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotcurrentLocationDiscoveryplusxml: MediaType =
      MediaType("application", "vnd.3gpp.current-location-discovery+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotgmopplusxml: MediaType =
      MediaType("application", "vnd.3gpp.gmop+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotgtpc: MediaType =
      MediaType("application", "vnd.3gpp.gtpc", compressible = false, binary = true)

    lazy val vnddot3gppdotinterworkingData: MediaType =
      MediaType("application", "vnd.3gpp.interworking-data", compressible = false, binary = true)

    lazy val vnddot3gppdotlpp: MediaType =
      MediaType("application", "vnd.3gpp.lpp", compressible = false, binary = true)

    lazy val vnddot3gppdotmcSignallingEar: MediaType =
      MediaType("application", "vnd.3gpp.mc-signalling-ear", compressible = false, binary = true)

    lazy val vnddot3gppdotmcdataAffiliationCommandplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-affiliation-command+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcdataInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcdataMsgstoreCtrlRequestplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-msgstore-ctrl-request+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcdataPayload: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-payload", compressible = false, binary = true)

    lazy val vnddot3gppdotmcdataRegroupplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-regroup+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcdataServiceConfigplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-service-config+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcdataSignalling: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-signalling", compressible = false, binary = true)

    lazy val vnddot3gppdotmcdataUeConfigplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-ue-config+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcdataUserProfileplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-user-profile+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttAffiliationCommandplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-affiliation-command+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttFloorRequestplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-floor-request+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttLocationInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-location-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttMbmsUsageInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-mbms-usage-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttRegroupplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-regroup+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttServiceConfigplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-service-config+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttSignedplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-signed+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttUeConfigplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-ue-config+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttUeInitConfigplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-ue-init-config+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcpttUserProfileplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-user-profile+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcvideoAffiliationCommandplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-affiliation-command+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcvideoInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcvideoLocationInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-location-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcvideoMbmsUsageInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-mbms-usage-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcvideoRegroupplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-regroup+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcvideoServiceConfigplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-service-config+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcvideoTransmissionRequestplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-transmission-request+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcvideoUeConfigplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-ue-config+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmcvideoUserProfileplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-user-profile+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotmidCallplusxml: MediaType =
      MediaType("application", "vnd.3gpp.mid-call+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotngap: MediaType =
      MediaType("application", "vnd.3gpp.ngap", compressible = false, binary = true)

    lazy val vnddot3gppdotpfcp: MediaType =
      MediaType("application", "vnd.3gpp.pfcp", compressible = false, binary = true)

    lazy val vnddot3gppdotpicBwLarge: MediaType =
      MediaType(
        "application",
        "vnd.3gpp.pic-bw-large",
        compressible = false,
        binary = true,
        fileExtensions = List("plb")
      )

    lazy val vnddot3gppdotpicBwSmall: MediaType =
      MediaType(
        "application",
        "vnd.3gpp.pic-bw-small",
        compressible = false,
        binary = true,
        fileExtensions = List("psb")
      )

    lazy val vnddot3gppdotpicBwVar: MediaType =
      MediaType("application", "vnd.3gpp.pic-bw-var", compressible = false, binary = true, fileExtensions = List("pvb"))

    lazy val vnddot3gppdotpinappInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.pinapp-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdots1ap: MediaType =
      MediaType("application", "vnd.3gpp.s1ap", compressible = false, binary = true)

    lazy val vnddot3gppdotsealAppCommRequirementsInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-app-comm-requirements-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealDataDeliveryInfopluscbor: MediaType =
      MediaType("application", "vnd.3gpp.seal-data-delivery-info+cbor", compressible = false, binary = true)

    lazy val vnddot3gppdotsealDataDeliveryInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-data-delivery-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealGroupDocplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-group-doc+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealLocationInfopluscbor: MediaType =
      MediaType("application", "vnd.3gpp.seal-location-info+cbor", compressible = false, binary = true)

    lazy val vnddot3gppdotsealLocationInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-location-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealMbmsUsageInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-mbms-usage-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealMbsUsageInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-mbs-usage-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealNetworkQosManagementInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-network-qos-management-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealNetworkResourceInfopluscbor: MediaType =
      MediaType("application", "vnd.3gpp.seal-network-resource-info+cbor", compressible = false, binary = true)

    lazy val vnddot3gppdotsealUeConfigInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-ue-config-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealUnicastInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-unicast-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsealUserProfileInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.seal-user-profile-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsms: MediaType =
      MediaType("application", "vnd.3gpp.sms", compressible = false, binary = true)

    lazy val vnddot3gppdotsmsplusxml: MediaType =
      MediaType("application", "vnd.3gpp.sms+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsrvccExtplusxml: MediaType =
      MediaType("application", "vnd.3gpp.srvcc-ext+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotsrvccInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.srvcc-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotstateAndEventInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.state-and-event-info+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotussdplusxml: MediaType =
      MediaType("application", "vnd.3gpp.ussd+xml", compressible = true, binary = true)

    lazy val vnddot3gppdotv2x: MediaType =
      MediaType("application", "vnd.3gpp.v2x", compressible = false, binary = true)

    lazy val vnddot3gppdotvaeInfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp.vae-info+xml", compressible = true, binary = true)

    lazy val vnddot3gpp2dotbcmcsinfoplusxml: MediaType =
      MediaType("application", "vnd.3gpp2.bcmcsinfo+xml", compressible = true, binary = true)

    lazy val vnddot3gpp2dotsms: MediaType =
      MediaType("application", "vnd.3gpp2.sms", compressible = false, binary = true)

    lazy val vnddot3gpp2dottcap: MediaType =
      MediaType("application", "vnd.3gpp2.tcap", compressible = false, binary = true, fileExtensions = List("tcap"))

    lazy val vnddot3lightssoftwaredotimagescal: MediaType =
      MediaType("application", "vnd.3lightssoftware.imagescal", compressible = false, binary = true)

    lazy val vnddot3mdotpostItNotes: MediaType =
      MediaType(
        "application",
        "vnd.3m.post-it-notes",
        compressible = false,
        binary = true,
        fileExtensions = List("pwn")
      )

    lazy val vnddotaccpacdotsimplydotaso: MediaType =
      MediaType(
        "application",
        "vnd.accpac.simply.aso",
        compressible = false,
        binary = true,
        fileExtensions = List("aso")
      )

    lazy val vnddotaccpacdotsimplydotimp: MediaType =
      MediaType(
        "application",
        "vnd.accpac.simply.imp",
        compressible = false,
        binary = true,
        fileExtensions = List("imp")
      )

    lazy val vnddotacmdotaddressxferplusjson: MediaType =
      MediaType("application", "vnd.acm.addressxfer+json", compressible = true, binary = false)

    lazy val vnddotacmdotchatbotplusjson: MediaType =
      MediaType("application", "vnd.acm.chatbot+json", compressible = true, binary = false)

    lazy val vnddotacucobol: MediaType =
      MediaType("application", "vnd.acucobol", compressible = false, binary = true, fileExtensions = List("acu"))

    lazy val vnddotacucorp: MediaType =
      MediaType(
        "application",
        "vnd.acucorp",
        compressible = false,
        binary = true,
        fileExtensions = List("atc", "acutc")
      )

    lazy val vnddotadobedotairApplicationInstallerPackagepluszip: MediaType =
      MediaType(
        "application",
        "vnd.adobe.air-application-installer-package+zip",
        compressible = false,
        binary = true,
        fileExtensions = List("air")
      )

    lazy val vnddotadobedotflashdotmovie: MediaType =
      MediaType("application", "vnd.adobe.flash.movie", compressible = false, binary = true)

    lazy val vnddotadobedotformscentraldotfcdt: MediaType =
      MediaType(
        "application",
        "vnd.adobe.formscentral.fcdt",
        compressible = false,
        binary = true,
        fileExtensions = List("fcdt")
      )

    lazy val vnddotadobedotfxp: MediaType =
      MediaType(
        "application",
        "vnd.adobe.fxp",
        compressible = false,
        binary = true,
        fileExtensions = List("fxp", "fxpl")
      )

    lazy val vnddotadobedotpartialUpload: MediaType =
      MediaType("application", "vnd.adobe.partial-upload", compressible = false, binary = true)

    lazy val vnddotadobedotxdpplusxml: MediaType =
      MediaType("application", "vnd.adobe.xdp+xml", compressible = true, binary = true, fileExtensions = List("xdp"))

    lazy val vnddotadobedotxfdf: MediaType =
      MediaType("application", "vnd.adobe.xfdf", compressible = false, binary = true, fileExtensions = List("xfdf"))

    lazy val vnddotaetherdotimp: MediaType =
      MediaType("application", "vnd.aether.imp", compressible = false, binary = true)

    lazy val vnddotafpcdotafplinedata: MediaType =
      MediaType("application", "vnd.afpc.afplinedata", compressible = false, binary = true)

    lazy val vnddotafpcdotafplinedataPagedef: MediaType =
      MediaType("application", "vnd.afpc.afplinedata-pagedef", compressible = false, binary = true)

    lazy val vnddotafpcdotcmocaCmresource: MediaType =
      MediaType("application", "vnd.afpc.cmoca-cmresource", compressible = false, binary = true)

    lazy val vnddotafpcdotfocaCharset: MediaType =
      MediaType("application", "vnd.afpc.foca-charset", compressible = false, binary = true)

    lazy val vnddotafpcdotfocaCodedfont: MediaType =
      MediaType("application", "vnd.afpc.foca-codedfont", compressible = false, binary = true)

    lazy val vnddotafpcdotfocaCodepage: MediaType =
      MediaType("application", "vnd.afpc.foca-codepage", compressible = false, binary = true)

    lazy val vnddotafpcdotmodca: MediaType =
      MediaType("application", "vnd.afpc.modca", compressible = false, binary = true)

    lazy val vnddotafpcdotmodcaCmtable: MediaType =
      MediaType("application", "vnd.afpc.modca-cmtable", compressible = false, binary = true)

    lazy val vnddotafpcdotmodcaFormdef: MediaType =
      MediaType("application", "vnd.afpc.modca-formdef", compressible = false, binary = true)

    lazy val vnddotafpcdotmodcaMediummap: MediaType =
      MediaType("application", "vnd.afpc.modca-mediummap", compressible = false, binary = true)

    lazy val vnddotafpcdotmodcaObjectcontainer: MediaType =
      MediaType("application", "vnd.afpc.modca-objectcontainer", compressible = false, binary = true)

    lazy val vnddotafpcdotmodcaOverlay: MediaType =
      MediaType("application", "vnd.afpc.modca-overlay", compressible = false, binary = true)

    lazy val vnddotafpcdotmodcaPagesegment: MediaType =
      MediaType("application", "vnd.afpc.modca-pagesegment", compressible = false, binary = true)

    lazy val vnddotage: MediaType =
      MediaType("application", "vnd.age", compressible = false, binary = true, fileExtensions = List("age"))

    lazy val vnddotahBarcode: MediaType =
      MediaType("application", "vnd.ah-barcode", compressible = false, binary = true)

    lazy val vnddotaheaddotspace: MediaType =
      MediaType("application", "vnd.ahead.space", compressible = false, binary = true, fileExtensions = List("ahead"))

    lazy val vnddotaia: MediaType =
      MediaType("application", "vnd.aia", compressible = false, binary = true)

    lazy val vnddotairzipdotfilesecuredotazf: MediaType =
      MediaType(
        "application",
        "vnd.airzip.filesecure.azf",
        compressible = false,
        binary = true,
        fileExtensions = List("azf")
      )

    lazy val vnddotairzipdotfilesecuredotazs: MediaType =
      MediaType(
        "application",
        "vnd.airzip.filesecure.azs",
        compressible = false,
        binary = true,
        fileExtensions = List("azs")
      )

    lazy val vnddotamadeusplusjson: MediaType =
      MediaType("application", "vnd.amadeus+json", compressible = true, binary = false)

    lazy val vnddotamazondotebook: MediaType =
      MediaType("application", "vnd.amazon.ebook", compressible = false, binary = true, fileExtensions = List("azw"))

    lazy val vnddotamazondotmobi8Ebook: MediaType =
      MediaType("application", "vnd.amazon.mobi8-ebook", compressible = false, binary = true)

    lazy val vnddotamericandynamicsdotacc: MediaType =
      MediaType(
        "application",
        "vnd.americandynamics.acc",
        compressible = false,
        binary = true,
        fileExtensions = List("acc")
      )

    lazy val vnddotamigadotami: MediaType =
      MediaType("application", "vnd.amiga.ami", compressible = false, binary = true, fileExtensions = List("ami"))

    lazy val vnddotamundsendotmazeplusxml: MediaType =
      MediaType("application", "vnd.amundsen.maze+xml", compressible = true, binary = true)

    lazy val vnddotandroiddotota: MediaType =
      MediaType("application", "vnd.android.ota", compressible = false, binary = true)

    lazy val vnddotandroiddotpackageArchive: MediaType =
      MediaType(
        "application",
        "vnd.android.package-archive",
        compressible = false,
        binary = true,
        fileExtensions = List("apk")
      )

    lazy val vnddotanki: MediaType =
      MediaType("application", "vnd.anki", compressible = false, binary = true)

    lazy val vnddotanserWebCertificateIssueInitiation: MediaType =
      MediaType(
        "application",
        "vnd.anser-web-certificate-issue-initiation",
        compressible = false,
        binary = true,
        fileExtensions = List("cii")
      )

    lazy val vnddotanserWebFundsTransferInitiation: MediaType =
      MediaType(
        "application",
        "vnd.anser-web-funds-transfer-initiation",
        compressible = false,
        binary = true,
        fileExtensions = List("fti")
      )

    lazy val vnddotantixdotgameComponent: MediaType =
      MediaType(
        "application",
        "vnd.antix.game-component",
        compressible = false,
        binary = true,
        fileExtensions = List("atx")
      )

    lazy val vnddotapachedotarrowdotfile: MediaType =
      MediaType("application", "vnd.apache.arrow.file", compressible = false, binary = true)

    lazy val vnddotapachedotarrowdotstream: MediaType =
      MediaType("application", "vnd.apache.arrow.stream", compressible = false, binary = true)

    lazy val vnddotapachedotparquet: MediaType =
      MediaType(
        "application",
        "vnd.apache.parquet",
        compressible = false,
        binary = true,
        fileExtensions = List("parquet")
      )

    lazy val vnddotapachedotthriftdotbinary: MediaType =
      MediaType("application", "vnd.apache.thrift.binary", compressible = false, binary = true)

    lazy val vnddotapachedotthriftdotcompact: MediaType =
      MediaType("application", "vnd.apache.thrift.compact", compressible = false, binary = true)

    lazy val vnddotapachedotthriftdotjson: MediaType =
      MediaType("application", "vnd.apache.thrift.json", compressible = false, binary = false)

    lazy val vnddotapexlang: MediaType =
      MediaType("application", "vnd.apexlang", compressible = false, binary = true)

    lazy val vnddotapiplusjson: MediaType =
      MediaType("application", "vnd.api+json", compressible = true, binary = false)

    lazy val vnddotaplextordotwarrpplusjson: MediaType =
      MediaType("application", "vnd.aplextor.warrp+json", compressible = true, binary = false)

    lazy val vnddotapothekendedotreservationplusjson: MediaType =
      MediaType("application", "vnd.apothekende.reservation+json", compressible = true, binary = false)

    lazy val vnddotappledotinstallerplusxml: MediaType =
      MediaType(
        "application",
        "vnd.apple.installer+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("mpkg")
      )

    lazy val vnddotappledotkeynote: MediaType =
      MediaType("application", "vnd.apple.keynote", compressible = false, binary = true, fileExtensions = List("key"))

    lazy val vnddotappledotmpegurl: MediaType =
      MediaType("application", "vnd.apple.mpegurl", compressible = false, binary = true, fileExtensions = List("m3u8"))

    lazy val vnddotappledotnumbers: MediaType =
      MediaType(
        "application",
        "vnd.apple.numbers",
        compressible = false,
        binary = true,
        fileExtensions = List("numbers")
      )

    lazy val vnddotappledotpages: MediaType =
      MediaType("application", "vnd.apple.pages", compressible = false, binary = true, fileExtensions = List("pages"))

    lazy val vnddotappledotpkpass: MediaType =
      MediaType("application", "vnd.apple.pkpass", compressible = false, binary = true, fileExtensions = List("pkpass"))

    lazy val vnddotarastradotswi: MediaType =
      MediaType("application", "vnd.arastra.swi", compressible = false, binary = true)

    lazy val vnddotaristanetworksdotswi: MediaType =
      MediaType(
        "application",
        "vnd.aristanetworks.swi",
        compressible = false,
        binary = true,
        fileExtensions = List("swi")
      )

    lazy val vnddotartisanplusjson: MediaType =
      MediaType("application", "vnd.artisan+json", compressible = true, binary = false)

    lazy val vnddotartsquare: MediaType =
      MediaType("application", "vnd.artsquare", compressible = false, binary = true)

    lazy val vnddotas207960dotvasdotconfigplusjer: MediaType =
      MediaType("application", "vnd.as207960.vas.config+jer", compressible = false, binary = true)

    lazy val vnddotas207960dotvasdotconfigplusuper: MediaType =
      MediaType("application", "vnd.as207960.vas.config+uper", compressible = false, binary = true)

    lazy val vnddotas207960dotvasdottapplusjer: MediaType =
      MediaType("application", "vnd.as207960.vas.tap+jer", compressible = false, binary = true)

    lazy val vnddotas207960dotvasdottapplusuper: MediaType =
      MediaType("application", "vnd.as207960.vas.tap+uper", compressible = false, binary = true)

    lazy val vnddotastraeaSoftwaredotiota: MediaType =
      MediaType(
        "application",
        "vnd.astraea-software.iota",
        compressible = false,
        binary = true,
        fileExtensions = List("iota")
      )

    lazy val vnddotaudiograph: MediaType =
      MediaType("application", "vnd.audiograph", compressible = false, binary = true, fileExtensions = List("aep"))

    lazy val vnddotautodeskdotfbx: MediaType =
      MediaType("application", "vnd.autodesk.fbx", compressible = false, binary = true, fileExtensions = List("fbx"))

    lazy val vnddotautopackage: MediaType =
      MediaType("application", "vnd.autopackage", compressible = false, binary = true)

    lazy val vnddotavalonplusjson: MediaType =
      MediaType("application", "vnd.avalon+json", compressible = true, binary = false)

    lazy val vnddotavistarplusxml: MediaType =
      MediaType("application", "vnd.avistar+xml", compressible = true, binary = true)

    lazy val vnddotbalsamiqdotbmmlplusxml: MediaType =
      MediaType(
        "application",
        "vnd.balsamiq.bmml+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("bmml")
      )

    lazy val vnddotbalsamiqdotbmpr: MediaType =
      MediaType("application", "vnd.balsamiq.bmpr", compressible = false, binary = true)

    lazy val vnddotbananaAccounting: MediaType =
      MediaType("application", "vnd.banana-accounting", compressible = false, binary = true)

    lazy val vnddotbbfdotuspdoterror: MediaType =
      MediaType("application", "vnd.bbf.usp.error", compressible = false, binary = true)

    lazy val vnddotbbfdotuspdotmsg: MediaType =
      MediaType("application", "vnd.bbf.usp.msg", compressible = false, binary = true)

    lazy val vnddotbbfdotuspdotmsgplusjson: MediaType =
      MediaType("application", "vnd.bbf.usp.msg+json", compressible = true, binary = false)

    lazy val vnddotbekitzurStechplusjson: MediaType =
      MediaType("application", "vnd.bekitzur-stech+json", compressible = true, binary = false)

    lazy val vnddotbelightsoftdotlhzdpluszip: MediaType =
      MediaType("application", "vnd.belightsoft.lhzd+zip", compressible = false, binary = true)

    lazy val vnddotbelightsoftdotlhzlpluszip: MediaType =
      MediaType("application", "vnd.belightsoft.lhzl+zip", compressible = false, binary = true)

    lazy val vnddotbintdotmedContent: MediaType =
      MediaType("application", "vnd.bint.med-content", compressible = false, binary = true)

    lazy val vnddotbiopaxdotrdfplusxml: MediaType =
      MediaType("application", "vnd.biopax.rdf+xml", compressible = true, binary = true)

    lazy val vnddotblinkIdbValueWrapper: MediaType =
      MediaType("application", "vnd.blink-idb-value-wrapper", compressible = false, binary = true)

    lazy val vnddotblueicedotmultipass: MediaType =
      MediaType(
        "application",
        "vnd.blueice.multipass",
        compressible = false,
        binary = true,
        fileExtensions = List("mpm")
      )

    lazy val vnddotbluetoothdotepdotoob: MediaType =
      MediaType("application", "vnd.bluetooth.ep.oob", compressible = false, binary = true)

    lazy val vnddotbluetoothdotledotoob: MediaType =
      MediaType("application", "vnd.bluetooth.le.oob", compressible = false, binary = true)

    lazy val vnddotbmi: MediaType =
      MediaType("application", "vnd.bmi", compressible = false, binary = true, fileExtensions = List("bmi"))

    lazy val vnddotbpf: MediaType =
      MediaType("application", "vnd.bpf", compressible = false, binary = true)

    lazy val vnddotbpf3: MediaType =
      MediaType("application", "vnd.bpf3", compressible = false, binary = true)

    lazy val vnddotbusinessobjects: MediaType =
      MediaType("application", "vnd.businessobjects", compressible = false, binary = true, fileExtensions = List("rep"))

    lazy val vnddotbyudotuapiplusjson: MediaType =
      MediaType("application", "vnd.byu.uapi+json", compressible = true, binary = false)

    lazy val vnddotbzip3: MediaType =
      MediaType("application", "vnd.bzip3", compressible = false, binary = true)

    lazy val vnddotc3vocdotscheduleplusxml: MediaType =
      MediaType("application", "vnd.c3voc.schedule+xml", compressible = true, binary = true)

    lazy val vnddotcabJscript: MediaType =
      MediaType("application", "vnd.cab-jscript", compressible = false, binary = true)

    lazy val vnddotcanonCpdl: MediaType =
      MediaType("application", "vnd.canon-cpdl", compressible = false, binary = true)

    lazy val vnddotcanonLips: MediaType =
      MediaType("application", "vnd.canon-lips", compressible = false, binary = true)

    lazy val vnddotcapasystemsPgplusjson: MediaType =
      MediaType("application", "vnd.capasystems-pg+json", compressible = true, binary = false)

    lazy val vnddotcel: MediaType =
      MediaType("application", "vnd.cel", compressible = false, binary = true)

    lazy val vnddotcendiodotthinlincdotclientconf: MediaType =
      MediaType("application", "vnd.cendio.thinlinc.clientconf", compressible = false, binary = true)

    lazy val vnddotcenturySystemsdottcpStream: MediaType =
      MediaType("application", "vnd.century-systems.tcp_stream", compressible = false, binary = true)

    lazy val vnddotchemdrawplusxml: MediaType =
      MediaType("application", "vnd.chemdraw+xml", compressible = true, binary = true, fileExtensions = List("cdxml"))

    lazy val vnddotchessPgn: MediaType =
      MediaType("application", "vnd.chess-pgn", compressible = false, binary = true)

    lazy val vnddotchipnutsdotkaraokeMmd: MediaType =
      MediaType(
        "application",
        "vnd.chipnuts.karaoke-mmd",
        compressible = false,
        binary = true,
        fileExtensions = List("mmd")
      )

    lazy val vnddotciedi: MediaType =
      MediaType("application", "vnd.ciedi", compressible = false, binary = true)

    lazy val vnddotcinderella: MediaType =
      MediaType("application", "vnd.cinderella", compressible = false, binary = true, fileExtensions = List("cdy"))

    lazy val vnddotcirpackdotisdnExt: MediaType =
      MediaType("application", "vnd.cirpack.isdn-ext", compressible = false, binary = true)

    lazy val vnddotcitationstylesdotstyleplusxml: MediaType =
      MediaType(
        "application",
        "vnd.citationstyles.style+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("csl")
      )

    lazy val vnddotclaymore: MediaType =
      MediaType("application", "vnd.claymore", compressible = false, binary = true, fileExtensions = List("cla"))

    lazy val vnddotcloantodotrp9: MediaType =
      MediaType("application", "vnd.cloanto.rp9", compressible = false, binary = true, fileExtensions = List("rp9"))

    lazy val vnddotclonkdotc4group: MediaType =
      MediaType(
        "application",
        "vnd.clonk.c4group",
        compressible = false,
        binary = true,
        fileExtensions = List("c4g", "c4d", "c4f", "c4p", "c4u")
      )

    lazy val vnddotcluetrustdotcartomobileConfig: MediaType =
      MediaType(
        "application",
        "vnd.cluetrust.cartomobile-config",
        compressible = false,
        binary = true,
        fileExtensions = List("c11amc")
      )

    lazy val vnddotcluetrustdotcartomobileConfigPkg: MediaType =
      MediaType(
        "application",
        "vnd.cluetrust.cartomobile-config-pkg",
        compressible = false,
        binary = true,
        fileExtensions = List("c11amz")
      )

    lazy val vnddotcncfdothelmdotchartdotcontentdotv1dottarplusgzip: MediaType =
      MediaType("application", "vnd.cncf.helm.chart.content.v1.tar+gzip", compressible = false, binary = true)

    lazy val vnddotcncfdothelmdotchartdotprovenancedotv1dotprov: MediaType =
      MediaType("application", "vnd.cncf.helm.chart.provenance.v1.prov", compressible = false, binary = true)

    lazy val vnddotcncfdothelmdotconfigdotv1plusjson: MediaType =
      MediaType("application", "vnd.cncf.helm.config.v1+json", compressible = true, binary = false)

    lazy val vnddotcoffeescript: MediaType =
      MediaType("application", "vnd.coffeescript", compressible = false, binary = true)

    lazy val vnddotcollabiodotxodocumentsdotdocument: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.document", compressible = false, binary = true)

    lazy val vnddotcollabiodotxodocumentsdotdocumentTemplate: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.document-template", compressible = false, binary = true)

    lazy val vnddotcollabiodotxodocumentsdotpresentation: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.presentation", compressible = false, binary = true)

    lazy val vnddotcollabiodotxodocumentsdotpresentationTemplate: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.presentation-template", compressible = false, binary = true)

    lazy val vnddotcollabiodotxodocumentsdotspreadsheet: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.spreadsheet", compressible = false, binary = true)

    lazy val vnddotcollabiodotxodocumentsdotspreadsheetTemplate: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.spreadsheet-template", compressible = false, binary = true)

    lazy val vnddotcollectionplusjson: MediaType =
      MediaType("application", "vnd.collection+json", compressible = true, binary = false)

    lazy val vnddotcollectiondotdocplusjson: MediaType =
      MediaType("application", "vnd.collection.doc+json", compressible = true, binary = false)

    lazy val vnddotcollectiondotnextplusjson: MediaType =
      MediaType("application", "vnd.collection.next+json", compressible = true, binary = false)

    lazy val vnddotcomicbookpluszip: MediaType =
      MediaType("application", "vnd.comicbook+zip", compressible = false, binary = true)

    lazy val vnddotcomicbookRar: MediaType =
      MediaType("application", "vnd.comicbook-rar", compressible = false, binary = true)

    lazy val vnddotcommerceBattelle: MediaType =
      MediaType("application", "vnd.commerce-battelle", compressible = false, binary = true)

    lazy val vnddotcommonspace: MediaType =
      MediaType("application", "vnd.commonspace", compressible = false, binary = true, fileExtensions = List("csp"))

    lazy val vnddotcontactdotcmsg: MediaType =
      MediaType(
        "application",
        "vnd.contact.cmsg",
        compressible = false,
        binary = true,
        fileExtensions = List("cdbcmsg")
      )

    lazy val vnddotcoreosdotignitionplusjson: MediaType =
      MediaType("application", "vnd.coreos.ignition+json", compressible = true, binary = false)

    lazy val vnddotcosmocaller: MediaType =
      MediaType("application", "vnd.cosmocaller", compressible = false, binary = true, fileExtensions = List("cmc"))

    lazy val vnddotcrickdotclicker: MediaType =
      MediaType("application", "vnd.crick.clicker", compressible = false, binary = true, fileExtensions = List("clkx"))

    lazy val vnddotcrickdotclickerdotkeyboard: MediaType =
      MediaType(
        "application",
        "vnd.crick.clicker.keyboard",
        compressible = false,
        binary = true,
        fileExtensions = List("clkk")
      )

    lazy val vnddotcrickdotclickerdotpalette: MediaType =
      MediaType(
        "application",
        "vnd.crick.clicker.palette",
        compressible = false,
        binary = true,
        fileExtensions = List("clkp")
      )

    lazy val vnddotcrickdotclickerdottemplate: MediaType =
      MediaType(
        "application",
        "vnd.crick.clicker.template",
        compressible = false,
        binary = true,
        fileExtensions = List("clkt")
      )

    lazy val vnddotcrickdotclickerdotwordbank: MediaType =
      MediaType(
        "application",
        "vnd.crick.clicker.wordbank",
        compressible = false,
        binary = true,
        fileExtensions = List("clkw")
      )

    lazy val vnddotcriticaltoolsdotwbsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.criticaltools.wbs+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("wbs")
      )

    lazy val vnddotcryptiidotpipeplusjson: MediaType =
      MediaType("application", "vnd.cryptii.pipe+json", compressible = true, binary = false)

    lazy val vnddotcryptoShadeFile: MediaType =
      MediaType("application", "vnd.crypto-shade-file", compressible = false, binary = true)

    lazy val vnddotcryptomatordotencrypted: MediaType =
      MediaType("application", "vnd.cryptomator.encrypted", compressible = false, binary = true)

    lazy val vnddotcryptomatordotvault: MediaType =
      MediaType("application", "vnd.cryptomator.vault", compressible = false, binary = true)

    lazy val vnddotctcPosml: MediaType =
      MediaType("application", "vnd.ctc-posml", compressible = false, binary = true, fileExtensions = List("pml"))

    lazy val vnddotctctdotwsplusxml: MediaType =
      MediaType("application", "vnd.ctct.ws+xml", compressible = true, binary = true)

    lazy val vnddotcupsPdf: MediaType =
      MediaType("application", "vnd.cups-pdf", compressible = false, binary = true)

    lazy val vnddotcupsPostscript: MediaType =
      MediaType("application", "vnd.cups-postscript", compressible = false, binary = true)

    lazy val vnddotcupsPpd: MediaType =
      MediaType("application", "vnd.cups-ppd", compressible = false, binary = true, fileExtensions = List("ppd"))

    lazy val vnddotcupsRaster: MediaType =
      MediaType("application", "vnd.cups-raster", compressible = false, binary = true)

    lazy val vnddotcupsRaw: MediaType =
      MediaType("application", "vnd.cups-raw", compressible = false, binary = true)

    lazy val vnddotcurl: MediaType =
      MediaType("application", "vnd.curl", compressible = false, binary = true)

    lazy val vnddotcurldotcar: MediaType =
      MediaType("application", "vnd.curl.car", compressible = false, binary = true, fileExtensions = List("car"))

    lazy val vnddotcurldotpcurl: MediaType =
      MediaType("application", "vnd.curl.pcurl", compressible = false, binary = true, fileExtensions = List("pcurl"))

    lazy val vnddotcyandotdeandotrootplusxml: MediaType =
      MediaType("application", "vnd.cyan.dean.root+xml", compressible = true, binary = true)

    lazy val vnddotcybank: MediaType =
      MediaType("application", "vnd.cybank", compressible = false, binary = true)

    lazy val vnddotcyclonedxplusjson: MediaType =
      MediaType("application", "vnd.cyclonedx+json", compressible = true, binary = false)

    lazy val vnddotcyclonedxplusxml: MediaType =
      MediaType("application", "vnd.cyclonedx+xml", compressible = true, binary = true)

    lazy val vnddotd2ldotcoursepackage1p0pluszip: MediaType =
      MediaType("application", "vnd.d2l.coursepackage1p0+zip", compressible = false, binary = true)

    lazy val vnddotd3mDataset: MediaType =
      MediaType("application", "vnd.d3m-dataset", compressible = false, binary = true)

    lazy val vnddotd3mProblem: MediaType =
      MediaType("application", "vnd.d3m-problem", compressible = false, binary = true)

    lazy val vnddotdart: MediaType =
      MediaType("application", "vnd.dart", compressible = true, binary = true, fileExtensions = List("dart"))

    lazy val vnddotdataVisiondotrdz: MediaType =
      MediaType("application", "vnd.data-vision.rdz", compressible = false, binary = true, fileExtensions = List("rdz"))

    lazy val vnddotdatalog: MediaType =
      MediaType("application", "vnd.datalog", compressible = false, binary = true)

    lazy val vnddotdatapackageplusjson: MediaType =
      MediaType("application", "vnd.datapackage+json", compressible = true, binary = false)

    lazy val vnddotdataresourceplusjson: MediaType =
      MediaType("application", "vnd.dataresource+json", compressible = true, binary = false)

    lazy val vnddotdbf: MediaType =
      MediaType("application", "vnd.dbf", compressible = false, binary = true, fileExtensions = List("dbf"))

    lazy val vnddotdcmpplusxml: MediaType =
      MediaType("application", "vnd.dcmp+xml", compressible = true, binary = true, fileExtensions = List("dcmp"))

    lazy val vnddotdebiandotbinaryPackage: MediaType =
      MediaType("application", "vnd.debian.binary-package", compressible = false, binary = true)

    lazy val vnddotdecedotdata: MediaType =
      MediaType(
        "application",
        "vnd.dece.data",
        compressible = false,
        binary = true,
        fileExtensions = List("uvf", "uvvf", "uvd", "uvvd")
      )

    lazy val vnddotdecedotttmlplusxml: MediaType =
      MediaType(
        "application",
        "vnd.dece.ttml+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("uvt", "uvvt")
      )

    lazy val vnddotdecedotunspecified: MediaType =
      MediaType(
        "application",
        "vnd.dece.unspecified",
        compressible = false,
        binary = true,
        fileExtensions = List("uvx", "uvvx")
      )

    lazy val vnddotdecedotzip: MediaType =
      MediaType(
        "application",
        "vnd.dece.zip",
        compressible = false,
        binary = true,
        fileExtensions = List("uvz", "uvvz")
      )

    lazy val vnddotdenovodotfcselayoutLink: MediaType =
      MediaType(
        "application",
        "vnd.denovo.fcselayout-link",
        compressible = false,
        binary = true,
        fileExtensions = List("fe_launch")
      )

    lazy val vnddotdesmumedotmovie: MediaType =
      MediaType("application", "vnd.desmume.movie", compressible = false, binary = true)

    lazy val vnddotdirBidotplateDlNosuffix: MediaType =
      MediaType("application", "vnd.dir-bi.plate-dl-nosuffix", compressible = false, binary = true)

    lazy val vnddotdmdotdelegationplusxml: MediaType =
      MediaType("application", "vnd.dm.delegation+xml", compressible = true, binary = true)

    lazy val vnddotdna: MediaType =
      MediaType("application", "vnd.dna", compressible = false, binary = true, fileExtensions = List("dna"))

    lazy val vnddotdocumentplusjson: MediaType =
      MediaType("application", "vnd.document+json", compressible = true, binary = false)

    lazy val vnddotdolbydotmlp: MediaType =
      MediaType("application", "vnd.dolby.mlp", compressible = false, binary = true, fileExtensions = List("mlp"))

    lazy val vnddotdolbydotmobiledot1: MediaType =
      MediaType("application", "vnd.dolby.mobile.1", compressible = false, binary = true)

    lazy val vnddotdolbydotmobiledot2: MediaType =
      MediaType("application", "vnd.dolby.mobile.2", compressible = false, binary = true)

    lazy val vnddotdoremirdotscorecloudBinaryDocument: MediaType =
      MediaType("application", "vnd.doremir.scorecloud-binary-document", compressible = false, binary = true)

    lazy val vnddotdpgraph: MediaType =
      MediaType("application", "vnd.dpgraph", compressible = false, binary = true, fileExtensions = List("dpg"))

    lazy val vnddotdreamfactory: MediaType =
      MediaType("application", "vnd.dreamfactory", compressible = false, binary = true, fileExtensions = List("dfac"))

    lazy val vnddotdriveplusjson: MediaType =
      MediaType("application", "vnd.drive+json", compressible = true, binary = false)

    lazy val vnddotdsKeypoint: MediaType =
      MediaType("application", "vnd.ds-keypoint", compressible = false, binary = true, fileExtensions = List("kpxx"))

    lazy val vnddotdtgdotlocal: MediaType =
      MediaType("application", "vnd.dtg.local", compressible = false, binary = true)

    lazy val vnddotdtgdotlocaldotflash: MediaType =
      MediaType("application", "vnd.dtg.local.flash", compressible = false, binary = true)

    lazy val vnddotdtgdotlocaldothtml: MediaType =
      MediaType("application", "vnd.dtg.local.html", compressible = false, binary = true)

    lazy val vnddotdvbdotait: MediaType =
      MediaType("application", "vnd.dvb.ait", compressible = false, binary = true, fileExtensions = List("ait"))

    lazy val vnddotdvbdotdvbislplusxml: MediaType =
      MediaType("application", "vnd.dvb.dvbisl+xml", compressible = true, binary = true)

    lazy val vnddotdvbdotdvbj: MediaType =
      MediaType("application", "vnd.dvb.dvbj", compressible = false, binary = true)

    lazy val vnddotdvbdotesgcontainer: MediaType =
      MediaType("application", "vnd.dvb.esgcontainer", compressible = false, binary = true)

    lazy val vnddotdvbdotipdcdftnotifaccess: MediaType =
      MediaType("application", "vnd.dvb.ipdcdftnotifaccess", compressible = false, binary = true)

    lazy val vnddotdvbdotipdcesgaccess: MediaType =
      MediaType("application", "vnd.dvb.ipdcesgaccess", compressible = false, binary = true)

    lazy val vnddotdvbdotipdcesgaccess2: MediaType =
      MediaType("application", "vnd.dvb.ipdcesgaccess2", compressible = false, binary = true)

    lazy val vnddotdvbdotipdcesgpdd: MediaType =
      MediaType("application", "vnd.dvb.ipdcesgpdd", compressible = false, binary = true)

    lazy val vnddotdvbdotipdcroaming: MediaType =
      MediaType("application", "vnd.dvb.ipdcroaming", compressible = false, binary = true)

    lazy val vnddotdvbdotiptvdotalfecBase: MediaType =
      MediaType("application", "vnd.dvb.iptv.alfec-base", compressible = false, binary = true)

    lazy val vnddotdvbdotiptvdotalfecEnhancement: MediaType =
      MediaType("application", "vnd.dvb.iptv.alfec-enhancement", compressible = false, binary = true)

    lazy val vnddotdvbdotnotifAggregateRootplusxml: MediaType =
      MediaType("application", "vnd.dvb.notif-aggregate-root+xml", compressible = true, binary = true)

    lazy val vnddotdvbdotnotifContainerplusxml: MediaType =
      MediaType("application", "vnd.dvb.notif-container+xml", compressible = true, binary = true)

    lazy val vnddotdvbdotnotifGenericplusxml: MediaType =
      MediaType("application", "vnd.dvb.notif-generic+xml", compressible = true, binary = true)

    lazy val vnddotdvbdotnotifIaMsglistplusxml: MediaType =
      MediaType("application", "vnd.dvb.notif-ia-msglist+xml", compressible = true, binary = true)

    lazy val vnddotdvbdotnotifIaRegistrationRequestplusxml: MediaType =
      MediaType("application", "vnd.dvb.notif-ia-registration-request+xml", compressible = true, binary = true)

    lazy val vnddotdvbdotnotifIaRegistrationResponseplusxml: MediaType =
      MediaType("application", "vnd.dvb.notif-ia-registration-response+xml", compressible = true, binary = true)

    lazy val vnddotdvbdotnotifInitplusxml: MediaType =
      MediaType("application", "vnd.dvb.notif-init+xml", compressible = true, binary = true)

    lazy val vnddotdvbdotpfr: MediaType =
      MediaType("application", "vnd.dvb.pfr", compressible = false, binary = true)

    lazy val vnddotdvbdotservice: MediaType =
      MediaType("application", "vnd.dvb.service", compressible = false, binary = true, fileExtensions = List("svc"))

    lazy val vnddotdxr: MediaType =
      MediaType("application", "vnd.dxr", compressible = false, binary = true)

    lazy val vnddotdynageo: MediaType =
      MediaType("application", "vnd.dynageo", compressible = false, binary = true, fileExtensions = List("geo"))

    lazy val vnddotdzr: MediaType =
      MediaType("application", "vnd.dzr", compressible = false, binary = true)

    lazy val vnddoteasykaraokedotcdgdownload: MediaType =
      MediaType("application", "vnd.easykaraoke.cdgdownload", compressible = false, binary = true)

    lazy val vnddotecdisUpdate: MediaType =
      MediaType("application", "vnd.ecdis-update", compressible = false, binary = true)

    lazy val vnddotecipdotrlp: MediaType =
      MediaType("application", "vnd.ecip.rlp", compressible = false, binary = true)

    lazy val vnddoteclipsedotdittoplusjson: MediaType =
      MediaType("application", "vnd.eclipse.ditto+json", compressible = true, binary = false)

    lazy val vnddotecowindotchart: MediaType =
      MediaType("application", "vnd.ecowin.chart", compressible = false, binary = true, fileExtensions = List("mag"))

    lazy val vnddotecowindotfilerequest: MediaType =
      MediaType("application", "vnd.ecowin.filerequest", compressible = false, binary = true)

    lazy val vnddotecowindotfileupdate: MediaType =
      MediaType("application", "vnd.ecowin.fileupdate", compressible = false, binary = true)

    lazy val vnddotecowindotseries: MediaType =
      MediaType("application", "vnd.ecowin.series", compressible = false, binary = true)

    lazy val vnddotecowindotseriesrequest: MediaType =
      MediaType("application", "vnd.ecowin.seriesrequest", compressible = false, binary = true)

    lazy val vnddotecowindotseriesupdate: MediaType =
      MediaType("application", "vnd.ecowin.seriesupdate", compressible = false, binary = true)

    lazy val vnddotefidotimg: MediaType =
      MediaType("application", "vnd.efi.img", compressible = false, binary = true)

    lazy val vnddotefidotiso: MediaType =
      MediaType("application", "vnd.efi.iso", compressible = false, binary = true)

    lazy val vnddotelnpluszip: MediaType =
      MediaType("application", "vnd.eln+zip", compressible = false, binary = true)

    lazy val vnddotemclientdotaccessrequestplusxml: MediaType =
      MediaType("application", "vnd.emclient.accessrequest+xml", compressible = true, binary = true)

    lazy val vnddotenliven: MediaType =
      MediaType("application", "vnd.enliven", compressible = false, binary = true, fileExtensions = List("nml"))

    lazy val vnddotenphasedotenvoy: MediaType =
      MediaType("application", "vnd.enphase.envoy", compressible = false, binary = true)

    lazy val vnddoteprintsdotdataplusxml: MediaType =
      MediaType("application", "vnd.eprints.data+xml", compressible = true, binary = true)

    lazy val vnddotepsondotesf: MediaType =
      MediaType("application", "vnd.epson.esf", compressible = false, binary = true, fileExtensions = List("esf"))

    lazy val vnddotepsondotmsf: MediaType =
      MediaType("application", "vnd.epson.msf", compressible = false, binary = true, fileExtensions = List("msf"))

    lazy val vnddotepsondotquickanime: MediaType =
      MediaType(
        "application",
        "vnd.epson.quickanime",
        compressible = false,
        binary = true,
        fileExtensions = List("qam")
      )

    lazy val vnddotepsondotsalt: MediaType =
      MediaType("application", "vnd.epson.salt", compressible = false, binary = true, fileExtensions = List("slt"))

    lazy val vnddotepsondotssf: MediaType =
      MediaType("application", "vnd.epson.ssf", compressible = false, binary = true, fileExtensions = List("ssf"))

    lazy val vnddotericssondotquickcall: MediaType =
      MediaType("application", "vnd.ericsson.quickcall", compressible = false, binary = true)

    lazy val vnddoterofs: MediaType =
      MediaType("application", "vnd.erofs", compressible = false, binary = true)

    lazy val vnddotespassEspasspluszip: MediaType =
      MediaType("application", "vnd.espass-espass+zip", compressible = false, binary = true)

    lazy val vnddoteszigno3plusxml: MediaType =
      MediaType(
        "application",
        "vnd.eszigno3+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("es3", "et3")
      )

    lazy val vnddotetsidotaocplusxml: MediaType =
      MediaType("application", "vnd.etsi.aoc+xml", compressible = true, binary = true)

    lazy val vnddotetsidotasicEpluszip: MediaType =
      MediaType("application", "vnd.etsi.asic-e+zip", compressible = false, binary = true)

    lazy val vnddotetsidotasicSpluszip: MediaType =
      MediaType("application", "vnd.etsi.asic-s+zip", compressible = false, binary = true)

    lazy val vnddotetsidotcugplusxml: MediaType =
      MediaType("application", "vnd.etsi.cug+xml", compressible = true, binary = true)

    lazy val vnddotetsidotiptvcommandplusxml: MediaType =
      MediaType("application", "vnd.etsi.iptvcommand+xml", compressible = true, binary = true)

    lazy val vnddotetsidotiptvdiscoveryplusxml: MediaType =
      MediaType("application", "vnd.etsi.iptvdiscovery+xml", compressible = true, binary = true)

    lazy val vnddotetsidotiptvprofileplusxml: MediaType =
      MediaType("application", "vnd.etsi.iptvprofile+xml", compressible = true, binary = true)

    lazy val vnddotetsidotiptvsadBcplusxml: MediaType =
      MediaType("application", "vnd.etsi.iptvsad-bc+xml", compressible = true, binary = true)

    lazy val vnddotetsidotiptvsadCodplusxml: MediaType =
      MediaType("application", "vnd.etsi.iptvsad-cod+xml", compressible = true, binary = true)

    lazy val vnddotetsidotiptvsadNpvrplusxml: MediaType =
      MediaType("application", "vnd.etsi.iptvsad-npvr+xml", compressible = true, binary = true)

    lazy val vnddotetsidotiptvserviceplusxml: MediaType =
      MediaType("application", "vnd.etsi.iptvservice+xml", compressible = true, binary = true)

    lazy val vnddotetsidotiptvsyncplusxml: MediaType =
      MediaType("application", "vnd.etsi.iptvsync+xml", compressible = true, binary = true)

    lazy val vnddotetsidotiptvueprofileplusxml: MediaType =
      MediaType("application", "vnd.etsi.iptvueprofile+xml", compressible = true, binary = true)

    lazy val vnddotetsidotmcidplusxml: MediaType =
      MediaType("application", "vnd.etsi.mcid+xml", compressible = true, binary = true)

    lazy val vnddotetsidotmheg5: MediaType =
      MediaType("application", "vnd.etsi.mheg5", compressible = false, binary = true)

    lazy val vnddotetsidotoverloadControlPolicyDatasetplusxml: MediaType =
      MediaType("application", "vnd.etsi.overload-control-policy-dataset+xml", compressible = true, binary = true)

    lazy val vnddotetsidotpstnplusxml: MediaType =
      MediaType("application", "vnd.etsi.pstn+xml", compressible = true, binary = true)

    lazy val vnddotetsidotsciplusxml: MediaType =
      MediaType("application", "vnd.etsi.sci+xml", compressible = true, binary = true)

    lazy val vnddotetsidotsimservsplusxml: MediaType =
      MediaType("application", "vnd.etsi.simservs+xml", compressible = true, binary = true)

    lazy val vnddotetsidottimestampToken: MediaType =
      MediaType("application", "vnd.etsi.timestamp-token", compressible = false, binary = true)

    lazy val vnddotetsidottslplusxml: MediaType =
      MediaType("application", "vnd.etsi.tsl+xml", compressible = true, binary = true)

    lazy val vnddotetsidottsldotder: MediaType =
      MediaType("application", "vnd.etsi.tsl.der", compressible = false, binary = true)

    lazy val vnddoteudotkaspariandotcarplusjson: MediaType =
      MediaType("application", "vnd.eu.kasparian.car+json", compressible = true, binary = false)

    lazy val vnddoteudoradotdata: MediaType =
      MediaType("application", "vnd.eudora.data", compressible = false, binary = true)

    lazy val vnddotevolvdotecigdotprofile: MediaType =
      MediaType("application", "vnd.evolv.ecig.profile", compressible = false, binary = true)

    lazy val vnddotevolvdotecigdotsettings: MediaType =
      MediaType("application", "vnd.evolv.ecig.settings", compressible = false, binary = true)

    lazy val vnddotevolvdotecigdottheme: MediaType =
      MediaType("application", "vnd.evolv.ecig.theme", compressible = false, binary = true)

    lazy val vnddotexstreamEmpowerpluszip: MediaType =
      MediaType("application", "vnd.exstream-empower+zip", compressible = false, binary = true)

    lazy val vnddotexstreamPackage: MediaType =
      MediaType("application", "vnd.exstream-package", compressible = false, binary = true)

    lazy val vnddotezpixAlbum: MediaType =
      MediaType("application", "vnd.ezpix-album", compressible = false, binary = true, fileExtensions = List("ez2"))

    lazy val vnddotezpixPackage: MediaType =
      MediaType("application", "vnd.ezpix-package", compressible = false, binary = true, fileExtensions = List("ez3"))

    lazy val vnddotfSecuredotmobile: MediaType =
      MediaType("application", "vnd.f-secure.mobile", compressible = false, binary = true)

    lazy val vnddotfafplusyaml: MediaType =
      MediaType("application", "vnd.faf+yaml", compressible = false, binary = true)

    lazy val vnddotfamilysearchdotgedcompluszip: MediaType =
      MediaType("application", "vnd.familysearch.gedcom+zip", compressible = false, binary = true)

    lazy val vnddotfastcopyDiskImage: MediaType =
      MediaType("application", "vnd.fastcopy-disk-image", compressible = false, binary = true)

    lazy val vnddotfdf: MediaType =
      MediaType("application", "vnd.fdf", compressible = false, binary = true, fileExtensions = List("fdf"))

    lazy val vnddotfdsndotmseed: MediaType =
      MediaType("application", "vnd.fdsn.mseed", compressible = false, binary = true, fileExtensions = List("mseed"))

    lazy val vnddotfdsndotseed: MediaType =
      MediaType(
        "application",
        "vnd.fdsn.seed",
        compressible = false,
        binary = true,
        fileExtensions = List("seed", "dataless")
      )

    lazy val vnddotfdsndotstationxmlplusxml: MediaType =
      MediaType("application", "vnd.fdsn.stationxml+xml", compressible = true, binary = true)

    lazy val vnddotffsns: MediaType =
      MediaType("application", "vnd.ffsns", compressible = false, binary = true)

    lazy val vnddotfgb: MediaType =
      MediaType("application", "vnd.fgb", compressible = false, binary = true)

    lazy val vnddotficlabdotflbpluszip: MediaType =
      MediaType("application", "vnd.ficlab.flb+zip", compressible = false, binary = true)

    lazy val vnddotfilmitdotzfc: MediaType =
      MediaType("application", "vnd.filmit.zfc", compressible = false, binary = true)

    lazy val vnddotfints: MediaType =
      MediaType("application", "vnd.fints", compressible = false, binary = true)

    lazy val vnddotfiremonkeysdotcloudcell: MediaType =
      MediaType("application", "vnd.firemonkeys.cloudcell", compressible = false, binary = true)

    lazy val vnddotflographit: MediaType =
      MediaType("application", "vnd.flographit", compressible = false, binary = true, fileExtensions = List("gph"))

    lazy val vnddotfluxtimedotclip: MediaType =
      MediaType("application", "vnd.fluxtime.clip", compressible = false, binary = true, fileExtensions = List("ftc"))

    lazy val vnddotfontFontforgeSfd: MediaType =
      MediaType("application", "vnd.font-fontforge-sfd", compressible = false, binary = true)

    lazy val vnddotframemaker: MediaType =
      MediaType(
        "application",
        "vnd.framemaker",
        compressible = false,
        binary = true,
        fileExtensions = List("fm", "frame", "maker", "book")
      )

    lazy val vnddotfreelogdotcomic: MediaType =
      MediaType("application", "vnd.freelog.comic", compressible = false, binary = true)

    lazy val vnddotfrogansdotfnc: MediaType =
      MediaType("application", "vnd.frogans.fnc", compressible = false, binary = true, fileExtensions = List("fnc"))

    lazy val vnddotfrogansdotltf: MediaType =
      MediaType("application", "vnd.frogans.ltf", compressible = false, binary = true, fileExtensions = List("ltf"))

    lazy val vnddotfscdotweblaunch: MediaType =
      MediaType("application", "vnd.fsc.weblaunch", compressible = false, binary = true, fileExtensions = List("fsc"))

    lazy val vnddotfujifilmdotfbdotdocuworks: MediaType =
      MediaType("application", "vnd.fujifilm.fb.docuworks", compressible = false, binary = true)

    lazy val vnddotfujifilmdotfbdotdocuworksdotbinder: MediaType =
      MediaType("application", "vnd.fujifilm.fb.docuworks.binder", compressible = false, binary = true)

    lazy val vnddotfujifilmdotfbdotdocuworksdotcontainer: MediaType =
      MediaType("application", "vnd.fujifilm.fb.docuworks.container", compressible = false, binary = true)

    lazy val vnddotfujifilmdotfbdotjfiplusxml: MediaType =
      MediaType("application", "vnd.fujifilm.fb.jfi+xml", compressible = true, binary = true)

    lazy val vnddotfujitsudotoasys: MediaType =
      MediaType("application", "vnd.fujitsu.oasys", compressible = false, binary = true, fileExtensions = List("oas"))

    lazy val vnddotfujitsudotoasys2: MediaType =
      MediaType("application", "vnd.fujitsu.oasys2", compressible = false, binary = true, fileExtensions = List("oa2"))

    lazy val vnddotfujitsudotoasys3: MediaType =
      MediaType("application", "vnd.fujitsu.oasys3", compressible = false, binary = true, fileExtensions = List("oa3"))

    lazy val vnddotfujitsudotoasysgp: MediaType =
      MediaType("application", "vnd.fujitsu.oasysgp", compressible = false, binary = true, fileExtensions = List("fg5"))

    lazy val vnddotfujitsudotoasysprs: MediaType =
      MediaType(
        "application",
        "vnd.fujitsu.oasysprs",
        compressible = false,
        binary = true,
        fileExtensions = List("bh2")
      )

    lazy val vnddotfujixeroxdotartEx: MediaType =
      MediaType("application", "vnd.fujixerox.art-ex", compressible = false, binary = true)

    lazy val vnddotfujixeroxdotart4: MediaType =
      MediaType("application", "vnd.fujixerox.art4", compressible = false, binary = true)

    lazy val vnddotfujixeroxdotddd: MediaType =
      MediaType("application", "vnd.fujixerox.ddd", compressible = false, binary = true, fileExtensions = List("ddd"))

    lazy val vnddotfujixeroxdotdocuworks: MediaType =
      MediaType(
        "application",
        "vnd.fujixerox.docuworks",
        compressible = false,
        binary = true,
        fileExtensions = List("xdw")
      )

    lazy val vnddotfujixeroxdotdocuworksdotbinder: MediaType =
      MediaType(
        "application",
        "vnd.fujixerox.docuworks.binder",
        compressible = false,
        binary = true,
        fileExtensions = List("xbd")
      )

    lazy val vnddotfujixeroxdotdocuworksdotcontainer: MediaType =
      MediaType("application", "vnd.fujixerox.docuworks.container", compressible = false, binary = true)

    lazy val vnddotfujixeroxdothbpl: MediaType =
      MediaType("application", "vnd.fujixerox.hbpl", compressible = false, binary = true)

    lazy val vnddotfutMisnet: MediaType =
      MediaType("application", "vnd.fut-misnet", compressible = false, binary = true)

    lazy val vnddotfutoinpluscbor: MediaType =
      MediaType("application", "vnd.futoin+cbor", compressible = false, binary = true)

    lazy val vnddotfutoinplusjson: MediaType =
      MediaType("application", "vnd.futoin+json", compressible = true, binary = false)

    lazy val vnddotfuzzysheet: MediaType =
      MediaType("application", "vnd.fuzzysheet", compressible = false, binary = true, fileExtensions = List("fzs"))

    lazy val vnddotg3pixdotg3fc: MediaType =
      MediaType("application", "vnd.g3pix.g3fc", compressible = false, binary = true)

    lazy val vnddotga4ghdotpassportplusjwt: MediaType =
      MediaType("application", "vnd.ga4gh.passport+jwt", compressible = false, binary = true)

    lazy val vnddotgenomatixdottuxedo: MediaType =
      MediaType(
        "application",
        "vnd.genomatix.tuxedo",
        compressible = false,
        binary = true,
        fileExtensions = List("txd")
      )

    lazy val vnddotgenozip: MediaType =
      MediaType("application", "vnd.genozip", compressible = false, binary = true)

    lazy val vnddotgenticsdotgrdplusjson: MediaType =
      MediaType("application", "vnd.gentics.grd+json", compressible = true, binary = false)

    lazy val vnddotgentoodotcatmetadataplusxml: MediaType =
      MediaType("application", "vnd.gentoo.catmetadata+xml", compressible = true, binary = true)

    lazy val vnddotgentoodotebuild: MediaType =
      MediaType("application", "vnd.gentoo.ebuild", compressible = false, binary = true)

    lazy val vnddotgentoodoteclass: MediaType =
      MediaType("application", "vnd.gentoo.eclass", compressible = false, binary = true)

    lazy val vnddotgentoodotgpkg: MediaType =
      MediaType("application", "vnd.gentoo.gpkg", compressible = false, binary = true)

    lazy val vnddotgentoodotmanifest: MediaType =
      MediaType("application", "vnd.gentoo.manifest", compressible = false, binary = true)

    lazy val vnddotgentoodotpkgmetadataplusxml: MediaType =
      MediaType("application", "vnd.gentoo.pkgmetadata+xml", compressible = true, binary = true)

    lazy val vnddotgentoodotxpak: MediaType =
      MediaType("application", "vnd.gentoo.xpak", compressible = false, binary = true)

    lazy val vnddotgeoplusjson: MediaType =
      MediaType("application", "vnd.geo+json", compressible = true, binary = false)

    lazy val vnddotgeocubeplusxml: MediaType =
      MediaType("application", "vnd.geocube+xml", compressible = true, binary = true)

    lazy val vnddotgeogebradotfile: MediaType =
      MediaType("application", "vnd.geogebra.file", compressible = false, binary = true, fileExtensions = List("ggb"))

    lazy val vnddotgeogebradotpinboard: MediaType =
      MediaType("application", "vnd.geogebra.pinboard", compressible = false, binary = true)

    lazy val vnddotgeogebradotslides: MediaType =
      MediaType("application", "vnd.geogebra.slides", compressible = false, binary = true, fileExtensions = List("ggs"))

    lazy val vnddotgeogebradottool: MediaType =
      MediaType("application", "vnd.geogebra.tool", compressible = false, binary = true, fileExtensions = List("ggt"))

    lazy val vnddotgeometryExplorer: MediaType =
      MediaType(
        "application",
        "vnd.geometry-explorer",
        compressible = false,
        binary = true,
        fileExtensions = List("gex", "gre")
      )

    lazy val vnddotgeonext: MediaType =
      MediaType("application", "vnd.geonext", compressible = false, binary = true, fileExtensions = List("gxt"))

    lazy val vnddotgeoplan: MediaType =
      MediaType("application", "vnd.geoplan", compressible = false, binary = true, fileExtensions = List("g2w"))

    lazy val vnddotgeospace: MediaType =
      MediaType("application", "vnd.geospace", compressible = false, binary = true, fileExtensions = List("g3w"))

    lazy val vnddotgerber: MediaType =
      MediaType("application", "vnd.gerber", compressible = false, binary = true)

    lazy val vnddotglobalplatformdotcardContentMgt: MediaType =
      MediaType("application", "vnd.globalplatform.card-content-mgt", compressible = false, binary = true)

    lazy val vnddotglobalplatformdotcardContentMgtResponse: MediaType =
      MediaType("application", "vnd.globalplatform.card-content-mgt-response", compressible = false, binary = true)

    lazy val vnddotgmx: MediaType =
      MediaType("application", "vnd.gmx", compressible = false, binary = true, fileExtensions = List("gmx"))

    lazy val vnddotgnudottalerdotexchangeplusjson: MediaType =
      MediaType("application", "vnd.gnu.taler.exchange+json", compressible = true, binary = false)

    lazy val vnddotgnudottalerdotmerchantplusjson: MediaType =
      MediaType("application", "vnd.gnu.taler.merchant+json", compressible = true, binary = false)

    lazy val vnddotgoogleAppsdotaudio: MediaType =
      MediaType("application", "vnd.google-apps.audio", compressible = false, binary = true)

    lazy val vnddotgoogleAppsdotdocument: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.document",
        compressible = false,
        binary = true,
        fileExtensions = List("gdoc")
      )

    lazy val vnddotgoogleAppsdotdrawing: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.drawing",
        compressible = false,
        binary = true,
        fileExtensions = List("gdraw")
      )

    lazy val vnddotgoogleAppsdotdriveSdk: MediaType =
      MediaType("application", "vnd.google-apps.drive-sdk", compressible = false, binary = true)

    lazy val vnddotgoogleAppsdotfile: MediaType =
      MediaType("application", "vnd.google-apps.file", compressible = false, binary = true)

    lazy val vnddotgoogleAppsdotfolder: MediaType =
      MediaType("application", "vnd.google-apps.folder", compressible = false, binary = true)

    lazy val vnddotgoogleAppsdotform: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.form",
        compressible = false,
        binary = true,
        fileExtensions = List("gform")
      )

    lazy val vnddotgoogleAppsdotfusiontable: MediaType =
      MediaType("application", "vnd.google-apps.fusiontable", compressible = false, binary = true)

    lazy val vnddotgoogleAppsdotjam: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.jam",
        compressible = false,
        binary = true,
        fileExtensions = List("gjam")
      )

    lazy val vnddotgoogleAppsdotmailLayout: MediaType =
      MediaType("application", "vnd.google-apps.mail-layout", compressible = false, binary = true)

    lazy val vnddotgoogleAppsdotmap: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.map",
        compressible = false,
        binary = true,
        fileExtensions = List("gmap")
      )

    lazy val vnddotgoogleAppsdotphoto: MediaType =
      MediaType("application", "vnd.google-apps.photo", compressible = false, binary = true)

    lazy val vnddotgoogleAppsdotpresentation: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.presentation",
        compressible = false,
        binary = true,
        fileExtensions = List("gslides")
      )

    lazy val vnddotgoogleAppsdotscript: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.script",
        compressible = false,
        binary = true,
        fileExtensions = List("gscript")
      )

    lazy val vnddotgoogleAppsdotshortcut: MediaType =
      MediaType("application", "vnd.google-apps.shortcut", compressible = false, binary = true)

    lazy val vnddotgoogleAppsdotsite: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.site",
        compressible = false,
        binary = true,
        fileExtensions = List("gsite")
      )

    lazy val vnddotgoogleAppsdotspreadsheet: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.spreadsheet",
        compressible = false,
        binary = true,
        fileExtensions = List("gsheet")
      )

    lazy val vnddotgoogleAppsdotunknown: MediaType =
      MediaType("application", "vnd.google-apps.unknown", compressible = false, binary = true)

    lazy val vnddotgoogleAppsdotvideo: MediaType =
      MediaType("application", "vnd.google-apps.video", compressible = false, binary = true)

    lazy val vnddotgoogleEarthdotkmlplusxml: MediaType =
      MediaType(
        "application",
        "vnd.google-earth.kml+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("kml")
      )

    lazy val vnddotgoogleEarthdotkmz: MediaType =
      MediaType(
        "application",
        "vnd.google-earth.kmz",
        compressible = false,
        binary = true,
        fileExtensions = List("kmz")
      )

    lazy val vnddotgovdotskdoteFormplusxml: MediaType =
      MediaType("application", "vnd.gov.sk.e-form+xml", compressible = true, binary = true)

    lazy val vnddotgovdotskdoteFormpluszip: MediaType =
      MediaType("application", "vnd.gov.sk.e-form+zip", compressible = false, binary = true)

    lazy val vnddotgovdotskdotxmldatacontainerplusxml: MediaType =
      MediaType(
        "application",
        "vnd.gov.sk.xmldatacontainer+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("xdcf")
      )

    lazy val vnddotgpxseedotmapplusxml: MediaType =
      MediaType("application", "vnd.gpxsee.map+xml", compressible = true, binary = true)

    lazy val vnddotgrafeq: MediaType =
      MediaType("application", "vnd.grafeq", compressible = false, binary = true, fileExtensions = List("gqf", "gqs"))

    lazy val vnddotgridmp: MediaType =
      MediaType("application", "vnd.gridmp", compressible = false, binary = true)

    lazy val vnddotgrooveAccount: MediaType =
      MediaType("application", "vnd.groove-account", compressible = false, binary = true, fileExtensions = List("gac"))

    lazy val vnddotgrooveHelp: MediaType =
      MediaType("application", "vnd.groove-help", compressible = false, binary = true, fileExtensions = List("ghf"))

    lazy val vnddotgrooveIdentityMessage: MediaType =
      MediaType(
        "application",
        "vnd.groove-identity-message",
        compressible = false,
        binary = true,
        fileExtensions = List("gim")
      )

    lazy val vnddotgrooveInjector: MediaType =
      MediaType("application", "vnd.groove-injector", compressible = false, binary = true, fileExtensions = List("grv"))

    lazy val vnddotgrooveToolMessage: MediaType =
      MediaType(
        "application",
        "vnd.groove-tool-message",
        compressible = false,
        binary = true,
        fileExtensions = List("gtm")
      )

    lazy val vnddotgrooveToolTemplate: MediaType =
      MediaType(
        "application",
        "vnd.groove-tool-template",
        compressible = false,
        binary = true,
        fileExtensions = List("tpl")
      )

    lazy val vnddotgrooveVcard: MediaType =
      MediaType("application", "vnd.groove-vcard", compressible = false, binary = true, fileExtensions = List("vcg"))

    lazy val vnddothalplusjson: MediaType =
      MediaType("application", "vnd.hal+json", compressible = true, binary = false)

    lazy val vnddothalplusxml: MediaType =
      MediaType("application", "vnd.hal+xml", compressible = true, binary = true, fileExtensions = List("hal"))

    lazy val vnddothandheldEntertainmentplusxml: MediaType =
      MediaType(
        "application",
        "vnd.handheld-entertainment+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("zmm")
      )

    lazy val vnddothbci: MediaType =
      MediaType("application", "vnd.hbci", compressible = false, binary = true, fileExtensions = List("hbci"))

    lazy val vnddothcplusjson: MediaType =
      MediaType("application", "vnd.hc+json", compressible = true, binary = false)

    lazy val vnddothclBireports: MediaType =
      MediaType("application", "vnd.hcl-bireports", compressible = false, binary = true)

    lazy val vnddothdt: MediaType =
      MediaType("application", "vnd.hdt", compressible = false, binary = true)

    lazy val vnddotherokuplusjson: MediaType =
      MediaType("application", "vnd.heroku+json", compressible = true, binary = false)

    lazy val vnddothhedotlessonPlayer: MediaType =
      MediaType(
        "application",
        "vnd.hhe.lesson-player",
        compressible = false,
        binary = true,
        fileExtensions = List("les")
      )

    lazy val vnddothpHpgl: MediaType =
      MediaType("application", "vnd.hp-hpgl", compressible = false, binary = true, fileExtensions = List("hpgl"))

    lazy val vnddothpHpid: MediaType =
      MediaType("application", "vnd.hp-hpid", compressible = false, binary = true, fileExtensions = List("hpid"))

    lazy val vnddothpHps: MediaType =
      MediaType("application", "vnd.hp-hps", compressible = false, binary = true, fileExtensions = List("hps"))

    lazy val vnddothpJlyt: MediaType =
      MediaType("application", "vnd.hp-jlyt", compressible = false, binary = true, fileExtensions = List("jlt"))

    lazy val vnddothpPcl: MediaType =
      MediaType("application", "vnd.hp-pcl", compressible = false, binary = true, fileExtensions = List("pcl"))

    lazy val vnddothpPclxl: MediaType =
      MediaType("application", "vnd.hp-pclxl", compressible = false, binary = true, fileExtensions = List("pclxl"))

    lazy val vnddothsl: MediaType =
      MediaType("application", "vnd.hsl", compressible = false, binary = true)

    lazy val vnddothttphone: MediaType =
      MediaType("application", "vnd.httphone", compressible = false, binary = true)

    lazy val vnddothydrostatixdotsofData: MediaType =
      MediaType(
        "application",
        "vnd.hydrostatix.sof-data",
        compressible = false,
        binary = true,
        fileExtensions = List("sfd-hdstx")
      )

    lazy val vnddothyperplusjson: MediaType =
      MediaType("application", "vnd.hyper+json", compressible = true, binary = false)

    lazy val vnddothyperItemplusjson: MediaType =
      MediaType("application", "vnd.hyper-item+json", compressible = true, binary = false)

    lazy val vnddothyperdriveplusjson: MediaType =
      MediaType("application", "vnd.hyperdrive+json", compressible = true, binary = false)

    lazy val vnddothzn3dCrossword: MediaType =
      MediaType("application", "vnd.hzn-3d-crossword", compressible = false, binary = true)

    lazy val vnddotibmdotafplinedata: MediaType =
      MediaType("application", "vnd.ibm.afplinedata", compressible = false, binary = true)

    lazy val vnddotibmdotelectronicMedia: MediaType =
      MediaType("application", "vnd.ibm.electronic-media", compressible = false, binary = true)

    lazy val vnddotibmdotminipay: MediaType =
      MediaType("application", "vnd.ibm.minipay", compressible = false, binary = true, fileExtensions = List("mpy"))

    lazy val vnddotibmdotmodcap: MediaType =
      MediaType(
        "application",
        "vnd.ibm.modcap",
        compressible = false,
        binary = true,
        fileExtensions = List("afp", "listafp", "list3820")
      )

    lazy val vnddotibmdotrightsManagement: MediaType =
      MediaType(
        "application",
        "vnd.ibm.rights-management",
        compressible = false,
        binary = true,
        fileExtensions = List("irm")
      )

    lazy val vnddotibmdotsecureContainer: MediaType =
      MediaType(
        "application",
        "vnd.ibm.secure-container",
        compressible = false,
        binary = true,
        fileExtensions = List("sc")
      )

    lazy val vnddoticcprofile: MediaType =
      MediaType(
        "application",
        "vnd.iccprofile",
        compressible = false,
        binary = true,
        fileExtensions = List("icc", "icm")
      )

    lazy val vnddotieeedot1905: MediaType =
      MediaType("application", "vnd.ieee.1905", compressible = false, binary = true)

    lazy val vnddotigloader: MediaType =
      MediaType("application", "vnd.igloader", compressible = false, binary = true, fileExtensions = List("igl"))

    lazy val vnddotimagemeterdotfolderpluszip: MediaType =
      MediaType("application", "vnd.imagemeter.folder+zip", compressible = false, binary = true)

    lazy val vnddotimagemeterdotimagepluszip: MediaType =
      MediaType("application", "vnd.imagemeter.image+zip", compressible = false, binary = true)

    lazy val vnddotimmervisionIvp: MediaType =
      MediaType("application", "vnd.immervision-ivp", compressible = false, binary = true, fileExtensions = List("ivp"))

    lazy val vnddotimmervisionIvu: MediaType =
      MediaType("application", "vnd.immervision-ivu", compressible = false, binary = true, fileExtensions = List("ivu"))

    lazy val vnddotimsdotimsccv1p1: MediaType =
      MediaType("application", "vnd.ims.imsccv1p1", compressible = false, binary = true)

    lazy val vnddotimsdotimsccv1p2: MediaType =
      MediaType("application", "vnd.ims.imsccv1p2", compressible = false, binary = true)

    lazy val vnddotimsdotimsccv1p3: MediaType =
      MediaType("application", "vnd.ims.imsccv1p3", compressible = false, binary = true)

    lazy val vnddotimsdotlisdotv2dotresultplusjson: MediaType =
      MediaType("application", "vnd.ims.lis.v2.result+json", compressible = true, binary = false)

    lazy val vnddotimsdotltidotv2dottoolconsumerprofileplusjson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolconsumerprofile+json", compressible = true, binary = false)

    lazy val vnddotimsdotltidotv2dottoolproxyplusjson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolproxy+json", compressible = true, binary = false)

    lazy val vnddotimsdotltidotv2dottoolproxydotidplusjson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolproxy.id+json", compressible = true, binary = false)

    lazy val vnddotimsdotltidotv2dottoolsettingsplusjson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolsettings+json", compressible = true, binary = false)

    lazy val vnddotimsdotltidotv2dottoolsettingsdotsimpleplusjson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolsettings.simple+json", compressible = true, binary = false)

    lazy val vnddotinformedcontroldotrmsplusxml: MediaType =
      MediaType("application", "vnd.informedcontrol.rms+xml", compressible = true, binary = true)

    lazy val vnddotinformixVisionary: MediaType =
      MediaType("application", "vnd.informix-visionary", compressible = false, binary = true)

    lazy val vnddotinfotechdotproject: MediaType =
      MediaType("application", "vnd.infotech.project", compressible = false, binary = true)

    lazy val vnddotinfotechdotprojectplusxml: MediaType =
      MediaType("application", "vnd.infotech.project+xml", compressible = true, binary = true)

    lazy val vnddotinnopathdotwampdotnotification: MediaType =
      MediaType("application", "vnd.innopath.wamp.notification", compressible = false, binary = true)

    lazy val vnddotinsorsdotigm: MediaType =
      MediaType("application", "vnd.insors.igm", compressible = false, binary = true, fileExtensions = List("igm"))

    lazy val vnddotintercondotformnet: MediaType =
      MediaType(
        "application",
        "vnd.intercon.formnet",
        compressible = false,
        binary = true,
        fileExtensions = List("xpw", "xpx")
      )

    lazy val vnddotintergeo: MediaType =
      MediaType("application", "vnd.intergeo", compressible = false, binary = true, fileExtensions = List("i2g"))

    lazy val vnddotintertrustdotdigibox: MediaType =
      MediaType("application", "vnd.intertrust.digibox", compressible = false, binary = true)

    lazy val vnddotintertrustdotnncp: MediaType =
      MediaType("application", "vnd.intertrust.nncp", compressible = false, binary = true)

    lazy val vnddotintudotqbo: MediaType =
      MediaType("application", "vnd.intu.qbo", compressible = false, binary = true, fileExtensions = List("qbo"))

    lazy val vnddotintudotqfx: MediaType =
      MediaType("application", "vnd.intu.qfx", compressible = false, binary = true, fileExtensions = List("qfx"))

    lazy val vnddotipfsdotipnsRecord: MediaType =
      MediaType("application", "vnd.ipfs.ipns-record", compressible = false, binary = true)

    lazy val vnddotiplddotcar: MediaType =
      MediaType("application", "vnd.ipld.car", compressible = false, binary = true)

    lazy val vnddotiplddotdagCbor: MediaType =
      MediaType("application", "vnd.ipld.dag-cbor", compressible = false, binary = true)

    lazy val vnddotiplddotdagJson: MediaType =
      MediaType("application", "vnd.ipld.dag-json", compressible = false, binary = false)

    lazy val vnddotiplddotraw: MediaType =
      MediaType("application", "vnd.ipld.raw", compressible = false, binary = true)

    lazy val vnddotiptcdotg2dotcatalogitemplusxml: MediaType =
      MediaType("application", "vnd.iptc.g2.catalogitem+xml", compressible = true, binary = true)

    lazy val vnddotiptcdotg2dotconceptitemplusxml: MediaType =
      MediaType("application", "vnd.iptc.g2.conceptitem+xml", compressible = true, binary = true)

    lazy val vnddotiptcdotg2dotknowledgeitemplusxml: MediaType =
      MediaType("application", "vnd.iptc.g2.knowledgeitem+xml", compressible = true, binary = true)

    lazy val vnddotiptcdotg2dotnewsitemplusxml: MediaType =
      MediaType("application", "vnd.iptc.g2.newsitem+xml", compressible = true, binary = true)

    lazy val vnddotiptcdotg2dotnewsmessageplusxml: MediaType =
      MediaType("application", "vnd.iptc.g2.newsmessage+xml", compressible = true, binary = true)

    lazy val vnddotiptcdotg2dotpackageitemplusxml: MediaType =
      MediaType("application", "vnd.iptc.g2.packageitem+xml", compressible = true, binary = true)

    lazy val vnddotiptcdotg2dotplanningitemplusxml: MediaType =
      MediaType("application", "vnd.iptc.g2.planningitem+xml", compressible = true, binary = true)

    lazy val vnddotipunpluggeddotrcprofile: MediaType =
      MediaType(
        "application",
        "vnd.ipunplugged.rcprofile",
        compressible = false,
        binary = true,
        fileExtensions = List("rcprofile")
      )

    lazy val vnddotirepositorydotpackageplusxml: MediaType =
      MediaType(
        "application",
        "vnd.irepository.package+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("irp")
      )

    lazy val vnddotisXpr: MediaType =
      MediaType("application", "vnd.is-xpr", compressible = false, binary = true, fileExtensions = List("xpr"))

    lazy val vnddotisacdotfcs: MediaType =
      MediaType("application", "vnd.isac.fcs", compressible = false, binary = true, fileExtensions = List("fcs"))

    lazy val vnddotiso1178310pluszip: MediaType =
      MediaType("application", "vnd.iso11783-10+zip", compressible = false, binary = true)

    lazy val vnddotjam: MediaType =
      MediaType("application", "vnd.jam", compressible = false, binary = true, fileExtensions = List("jam"))

    lazy val vnddotjapannetDirectoryService: MediaType =
      MediaType("application", "vnd.japannet-directory-service", compressible = false, binary = true)

    lazy val vnddotjapannetJpnstoreWakeup: MediaType =
      MediaType("application", "vnd.japannet-jpnstore-wakeup", compressible = false, binary = true)

    lazy val vnddotjapannetPaymentWakeup: MediaType =
      MediaType("application", "vnd.japannet-payment-wakeup", compressible = false, binary = true)

    lazy val vnddotjapannetRegistration: MediaType =
      MediaType("application", "vnd.japannet-registration", compressible = false, binary = true)

    lazy val vnddotjapannetRegistrationWakeup: MediaType =
      MediaType("application", "vnd.japannet-registration-wakeup", compressible = false, binary = true)

    lazy val vnddotjapannetSetstoreWakeup: MediaType =
      MediaType("application", "vnd.japannet-setstore-wakeup", compressible = false, binary = true)

    lazy val vnddotjapannetVerification: MediaType =
      MediaType("application", "vnd.japannet-verification", compressible = false, binary = true)

    lazy val vnddotjapannetVerificationWakeup: MediaType =
      MediaType("application", "vnd.japannet-verification-wakeup", compressible = false, binary = true)

    lazy val vnddotjcpdotjavamedotmidletRms: MediaType =
      MediaType(
        "application",
        "vnd.jcp.javame.midlet-rms",
        compressible = false,
        binary = true,
        fileExtensions = List("rms")
      )

    lazy val vnddotjisp: MediaType =
      MediaType("application", "vnd.jisp", compressible = false, binary = true, fileExtensions = List("jisp"))

    lazy val vnddotjoostdotjodaArchive: MediaType =
      MediaType(
        "application",
        "vnd.joost.joda-archive",
        compressible = false,
        binary = true,
        fileExtensions = List("joda")
      )

    lazy val vnddotjskdotisdnNgn: MediaType =
      MediaType("application", "vnd.jsk.isdn-ngn", compressible = false, binary = true)

    lazy val vnddotkahootz: MediaType =
      MediaType("application", "vnd.kahootz", compressible = false, binary = true, fileExtensions = List("ktz", "ktr"))

    lazy val vnddotkdedotkarbon: MediaType =
      MediaType("application", "vnd.kde.karbon", compressible = false, binary = true, fileExtensions = List("karbon"))

    lazy val vnddotkdedotkchart: MediaType =
      MediaType("application", "vnd.kde.kchart", compressible = false, binary = true, fileExtensions = List("chrt"))

    lazy val vnddotkdedotkformula: MediaType =
      MediaType("application", "vnd.kde.kformula", compressible = false, binary = true, fileExtensions = List("kfo"))

    lazy val vnddotkdedotkivio: MediaType =
      MediaType("application", "vnd.kde.kivio", compressible = false, binary = true, fileExtensions = List("flw"))

    lazy val vnddotkdedotkontour: MediaType =
      MediaType("application", "vnd.kde.kontour", compressible = false, binary = true, fileExtensions = List("kon"))

    lazy val vnddotkdedotkpresenter: MediaType =
      MediaType(
        "application",
        "vnd.kde.kpresenter",
        compressible = false,
        binary = true,
        fileExtensions = List("kpr", "kpt")
      )

    lazy val vnddotkdedotkspread: MediaType =
      MediaType("application", "vnd.kde.kspread", compressible = false, binary = true, fileExtensions = List("ksp"))

    lazy val vnddotkdedotkword: MediaType =
      MediaType(
        "application",
        "vnd.kde.kword",
        compressible = false,
        binary = true,
        fileExtensions = List("kwd", "kwt")
      )

    lazy val vnddotkdl: MediaType =
      MediaType("application", "vnd.kdl", compressible = false, binary = true)

    lazy val vnddotkenameaapp: MediaType =
      MediaType("application", "vnd.kenameaapp", compressible = false, binary = true, fileExtensions = List("htke"))

    lazy val vnddotkeymandotkmppluszip: MediaType =
      MediaType("application", "vnd.keyman.kmp+zip", compressible = false, binary = true)

    lazy val vnddotkeymandotkmx: MediaType =
      MediaType("application", "vnd.keyman.kmx", compressible = false, binary = true)

    lazy val vnddotkidspiration: MediaType =
      MediaType("application", "vnd.kidspiration", compressible = false, binary = true, fileExtensions = List("kia"))

    lazy val vnddotkinar: MediaType =
      MediaType("application", "vnd.kinar", compressible = false, binary = true, fileExtensions = List("kne", "knp"))

    lazy val vnddotkoan: MediaType =
      MediaType(
        "application",
        "vnd.koan",
        compressible = false,
        binary = true,
        fileExtensions = List("skp", "skd", "skt", "skm")
      )

    lazy val vnddotkodakDescriptor: MediaType =
      MediaType(
        "application",
        "vnd.kodak-descriptor",
        compressible = false,
        binary = true,
        fileExtensions = List("sse")
      )

    lazy val vnddotlas: MediaType =
      MediaType("application", "vnd.las", compressible = false, binary = true)

    lazy val vnddotlasdotlasplusjson: MediaType =
      MediaType("application", "vnd.las.las+json", compressible = true, binary = false)

    lazy val vnddotlasdotlasplusxml: MediaType =
      MediaType("application", "vnd.las.las+xml", compressible = true, binary = true, fileExtensions = List("lasxml"))

    lazy val vnddotlaszip: MediaType =
      MediaType("application", "vnd.laszip", compressible = false, binary = true)

    lazy val vnddotldevdotproductlicensing: MediaType =
      MediaType("application", "vnd.ldev.productlicensing", compressible = false, binary = true)

    lazy val vnddotleapplusjson: MediaType =
      MediaType("application", "vnd.leap+json", compressible = true, binary = false)

    lazy val vnddotlibertyRequestplusxml: MediaType =
      MediaType("application", "vnd.liberty-request+xml", compressible = true, binary = true)

    lazy val vnddotllamagraphicsdotlifeBalancedotdesktop: MediaType =
      MediaType(
        "application",
        "vnd.llamagraphics.life-balance.desktop",
        compressible = false,
        binary = true,
        fileExtensions = List("lbd")
      )

    lazy val vnddotllamagraphicsdotlifeBalancedotexchangeplusxml: MediaType =
      MediaType(
        "application",
        "vnd.llamagraphics.life-balance.exchange+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("lbe")
      )

    lazy val vnddotlogipipedotcircuitpluszip: MediaType =
      MediaType("application", "vnd.logipipe.circuit+zip", compressible = false, binary = true)

    lazy val vnddotloom: MediaType =
      MediaType("application", "vnd.loom", compressible = false, binary = true)

    lazy val vnddotlotus123: MediaType =
      MediaType("application", "vnd.lotus-1-2-3", compressible = false, binary = true, fileExtensions = List("123"))

    lazy val vnddotlotusApproach: MediaType =
      MediaType("application", "vnd.lotus-approach", compressible = false, binary = true, fileExtensions = List("apr"))

    lazy val vnddotlotusFreelance: MediaType =
      MediaType("application", "vnd.lotus-freelance", compressible = false, binary = true, fileExtensions = List("pre"))

    lazy val vnddotlotusNotes: MediaType =
      MediaType("application", "vnd.lotus-notes", compressible = false, binary = true, fileExtensions = List("nsf"))

    lazy val vnddotlotusOrganizer: MediaType =
      MediaType("application", "vnd.lotus-organizer", compressible = false, binary = true, fileExtensions = List("org"))

    lazy val vnddotlotusScreencam: MediaType =
      MediaType("application", "vnd.lotus-screencam", compressible = false, binary = true, fileExtensions = List("scm"))

    lazy val vnddotlotusWordpro: MediaType =
      MediaType("application", "vnd.lotus-wordpro", compressible = false, binary = true, fileExtensions = List("lwp"))

    lazy val vnddotmacportsdotportpkg: MediaType =
      MediaType(
        "application",
        "vnd.macports.portpkg",
        compressible = false,
        binary = true,
        fileExtensions = List("portpkg")
      )

    lazy val vnddotmaml: MediaType =
      MediaType("application", "vnd.maml", compressible = false, binary = true)

    lazy val vnddotmapboxVectorTile: MediaType =
      MediaType(
        "application",
        "vnd.mapbox-vector-tile",
        compressible = false,
        binary = true,
        fileExtensions = List("mvt")
      )

    lazy val vnddotmarlindotdrmdotactiontokenplusxml: MediaType =
      MediaType("application", "vnd.marlin.drm.actiontoken+xml", compressible = true, binary = true)

    lazy val vnddotmarlindotdrmdotconftokenplusxml: MediaType =
      MediaType("application", "vnd.marlin.drm.conftoken+xml", compressible = true, binary = true)

    lazy val vnddotmarlindotdrmdotlicenseplusxml: MediaType =
      MediaType("application", "vnd.marlin.drm.license+xml", compressible = true, binary = true)

    lazy val vnddotmarlindotdrmdotmdcf: MediaType =
      MediaType("application", "vnd.marlin.drm.mdcf", compressible = false, binary = true)

    lazy val vnddotmasonplusjson: MediaType =
      MediaType("application", "vnd.mason+json", compressible = true, binary = false)

    lazy val vnddotmaxardotarchivedot3tzpluszip: MediaType =
      MediaType("application", "vnd.maxar.archive.3tz+zip", compressible = false, binary = true)

    lazy val vnddotmaxminddotmaxmindDb: MediaType =
      MediaType("application", "vnd.maxmind.maxmind-db", compressible = false, binary = true)

    lazy val vnddotmcd: MediaType =
      MediaType("application", "vnd.mcd", compressible = false, binary = true, fileExtensions = List("mcd"))

    lazy val vnddotmdl: MediaType =
      MediaType("application", "vnd.mdl", compressible = false, binary = true)

    lazy val vnddotmdlMbsdf: MediaType =
      MediaType("application", "vnd.mdl-mbsdf", compressible = false, binary = true)

    lazy val vnddotmedcalcdata: MediaType =
      MediaType("application", "vnd.medcalcdata", compressible = false, binary = true, fileExtensions = List("mc1"))

    lazy val vnddotmediastationdotcdkey: MediaType =
      MediaType(
        "application",
        "vnd.mediastation.cdkey",
        compressible = false,
        binary = true,
        fileExtensions = List("cdkey")
      )

    lazy val vnddotmedicalholodeckdotrecordxr: MediaType =
      MediaType("application", "vnd.medicalholodeck.recordxr", compressible = false, binary = true)

    lazy val vnddotmeridianSlingshot: MediaType =
      MediaType("application", "vnd.meridian-slingshot", compressible = false, binary = true)

    lazy val vnddotmermaid: MediaType =
      MediaType("application", "vnd.mermaid", compressible = false, binary = true)

    lazy val vnddotmfer: MediaType =
      MediaType("application", "vnd.mfer", compressible = false, binary = true, fileExtensions = List("mwf"))

    lazy val vnddotmfmp: MediaType =
      MediaType("application", "vnd.mfmp", compressible = false, binary = true, fileExtensions = List("mfm"))

    lazy val vnddotmicroplusjson: MediaType =
      MediaType("application", "vnd.micro+json", compressible = true, binary = false)

    lazy val vnddotmicrografxdotflo: MediaType =
      MediaType("application", "vnd.micrografx.flo", compressible = false, binary = true, fileExtensions = List("flo"))

    lazy val vnddotmicrografxdotigx: MediaType =
      MediaType("application", "vnd.micrografx.igx", compressible = false, binary = true, fileExtensions = List("igx"))

    lazy val vnddotmicrosoftdotportableExecutable: MediaType =
      MediaType("application", "vnd.microsoft.portable-executable", compressible = false, binary = true)

    lazy val vnddotmicrosoftdotwindowsdotthumbnailCache: MediaType =
      MediaType("application", "vnd.microsoft.windows.thumbnail-cache", compressible = false, binary = true)

    lazy val vnddotmieleplusjson: MediaType =
      MediaType("application", "vnd.miele+json", compressible = true, binary = false)

    lazy val vnddotmif: MediaType =
      MediaType("application", "vnd.mif", compressible = false, binary = true, fileExtensions = List("mif"))

    lazy val vnddotminisoftHp3000Save: MediaType =
      MediaType("application", "vnd.minisoft-hp3000-save", compressible = false, binary = true)

    lazy val vnddotmitsubishidotmistyGuarddottrustweb: MediaType =
      MediaType("application", "vnd.mitsubishi.misty-guard.trustweb", compressible = false, binary = true)

    lazy val vnddotmobiusdotdaf: MediaType =
      MediaType("application", "vnd.mobius.daf", compressible = false, binary = true, fileExtensions = List("daf"))

    lazy val vnddotmobiusdotdis: MediaType =
      MediaType("application", "vnd.mobius.dis", compressible = false, binary = true, fileExtensions = List("dis"))

    lazy val vnddotmobiusdotmbk: MediaType =
      MediaType("application", "vnd.mobius.mbk", compressible = false, binary = true, fileExtensions = List("mbk"))

    lazy val vnddotmobiusdotmqy: MediaType =
      MediaType("application", "vnd.mobius.mqy", compressible = false, binary = true, fileExtensions = List("mqy"))

    lazy val vnddotmobiusdotmsl: MediaType =
      MediaType("application", "vnd.mobius.msl", compressible = false, binary = true, fileExtensions = List("msl"))

    lazy val vnddotmobiusdotplc: MediaType =
      MediaType("application", "vnd.mobius.plc", compressible = false, binary = true, fileExtensions = List("plc"))

    lazy val vnddotmobiusdottxf: MediaType =
      MediaType("application", "vnd.mobius.txf", compressible = false, binary = true, fileExtensions = List("txf"))

    lazy val vnddotmodl: MediaType =
      MediaType("application", "vnd.modl", compressible = false, binary = true)

    lazy val vnddotmophundotapplication: MediaType =
      MediaType(
        "application",
        "vnd.mophun.application",
        compressible = false,
        binary = true,
        fileExtensions = List("mpn")
      )

    lazy val vnddotmophundotcertificate: MediaType =
      MediaType(
        "application",
        "vnd.mophun.certificate",
        compressible = false,
        binary = true,
        fileExtensions = List("mpc")
      )

    lazy val vnddotmotoroladotflexsuite: MediaType =
      MediaType("application", "vnd.motorola.flexsuite", compressible = false, binary = true)

    lazy val vnddotmotoroladotflexsuitedotadsi: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.adsi", compressible = false, binary = true)

    lazy val vnddotmotoroladotflexsuitedotfis: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.fis", compressible = false, binary = true)

    lazy val vnddotmotoroladotflexsuitedotgotap: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.gotap", compressible = false, binary = true)

    lazy val vnddotmotoroladotflexsuitedotkmr: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.kmr", compressible = false, binary = true)

    lazy val vnddotmotoroladotflexsuitedotttc: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.ttc", compressible = false, binary = true)

    lazy val vnddotmotoroladotflexsuitedotwem: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.wem", compressible = false, binary = true)

    lazy val vnddotmotoroladotiprm: MediaType =
      MediaType("application", "vnd.motorola.iprm", compressible = false, binary = true)

    lazy val vnddotmozilladotxulplusxml: MediaType =
      MediaType("application", "vnd.mozilla.xul+xml", compressible = true, binary = true, fileExtensions = List("xul"))

    lazy val vnddotms3mfdocument: MediaType =
      MediaType("application", "vnd.ms-3mfdocument", compressible = false, binary = true)

    lazy val vnddotmsArtgalry: MediaType =
      MediaType("application", "vnd.ms-artgalry", compressible = false, binary = true, fileExtensions = List("cil"))

    lazy val vnddotmsAsf: MediaType =
      MediaType("application", "vnd.ms-asf", compressible = false, binary = true)

    lazy val vnddotmsCabCompressed: MediaType =
      MediaType(
        "application",
        "vnd.ms-cab-compressed",
        compressible = false,
        binary = true,
        fileExtensions = List("cab")
      )

    lazy val vnddotmsColordoticcprofile: MediaType =
      MediaType("application", "vnd.ms-color.iccprofile", compressible = false, binary = true)

    lazy val vnddotmsExcel: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel",
        compressible = false,
        binary = true,
        fileExtensions = List("xls", "xlm", "xla", "xlc", "xlt", "xlw")
      )

    lazy val vnddotmsExceldotaddindotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel.addin.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("xlam")
      )

    lazy val vnddotmsExceldotsheetdotbinarydotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel.sheet.binary.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("xlsb")
      )

    lazy val vnddotmsExceldotsheetdotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel.sheet.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("xlsm")
      )

    lazy val vnddotmsExceldottemplatedotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel.template.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("xltm")
      )

    lazy val vnddotmsFontobject: MediaType =
      MediaType("application", "vnd.ms-fontobject", compressible = true, binary = true, fileExtensions = List("eot"))

    lazy val vnddotmsHtmlhelp: MediaType =
      MediaType("application", "vnd.ms-htmlhelp", compressible = false, binary = true, fileExtensions = List("chm"))

    lazy val vnddotmsIms: MediaType =
      MediaType("application", "vnd.ms-ims", compressible = false, binary = true, fileExtensions = List("ims"))

    lazy val vnddotmsLrm: MediaType =
      MediaType("application", "vnd.ms-lrm", compressible = false, binary = true, fileExtensions = List("lrm"))

    lazy val vnddotmsOfficedotactivexplusxml: MediaType =
      MediaType("application", "vnd.ms-office.activex+xml", compressible = true, binary = true)

    lazy val vnddotmsOfficetheme: MediaType =
      MediaType("application", "vnd.ms-officetheme", compressible = false, binary = true, fileExtensions = List("thmx"))

    lazy val vnddotmsOpentype: MediaType =
      MediaType("application", "vnd.ms-opentype", compressible = true, binary = true)

    lazy val vnddotmsOutlook: MediaType =
      MediaType("application", "vnd.ms-outlook", compressible = false, binary = true, fileExtensions = List("msg"))

    lazy val vnddotmsPackagedotobfuscatedOpentype: MediaType =
      MediaType("application", "vnd.ms-package.obfuscated-opentype", compressible = false, binary = true)

    lazy val vnddotmsPkidotseccat: MediaType =
      MediaType("application", "vnd.ms-pki.seccat", compressible = false, binary = true, fileExtensions = List("cat"))

    lazy val vnddotmsPkidotstl: MediaType =
      MediaType("application", "vnd.ms-pki.stl", compressible = false, binary = true, fileExtensions = List("stl"))

    lazy val vnddotmsPlayreadydotinitiatorplusxml: MediaType =
      MediaType("application", "vnd.ms-playready.initiator+xml", compressible = true, binary = true)

    lazy val vnddotmsPowerpoint: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint",
        compressible = false,
        binary = true,
        fileExtensions = List("ppt", "pps", "pot")
      )

    lazy val vnddotmsPowerpointdotaddindotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.addin.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("ppam")
      )

    lazy val vnddotmsPowerpointdotpresentationdotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.presentation.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("pptm")
      )

    lazy val vnddotmsPowerpointdotslidedotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.slide.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("sldm")
      )

    lazy val vnddotmsPowerpointdotslideshowdotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.slideshow.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("ppsm")
      )

    lazy val vnddotmsPowerpointdottemplatedotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.template.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("potm")
      )

    lazy val vnddotmsPrintdevicecapabilitiesplusxml: MediaType =
      MediaType("application", "vnd.ms-printdevicecapabilities+xml", compressible = true, binary = true)

    lazy val vnddotmsPrintingdotprintticketplusxml: MediaType =
      MediaType("application", "vnd.ms-printing.printticket+xml", compressible = true, binary = true)

    lazy val vnddotmsPrintschematicketplusxml: MediaType =
      MediaType("application", "vnd.ms-printschematicket+xml", compressible = true, binary = true)

    lazy val vnddotmsProject: MediaType =
      MediaType(
        "application",
        "vnd.ms-project",
        compressible = false,
        binary = true,
        fileExtensions = List("mpp", "mpt")
      )

    lazy val vnddotmsTnef: MediaType =
      MediaType("application", "vnd.ms-tnef", compressible = false, binary = true)

    lazy val vnddotmsVisiodotviewer: MediaType =
      MediaType("application", "vnd.ms-visio.viewer", compressible = false, binary = true, fileExtensions = List("vdx"))

    lazy val vnddotmsWindowsdotdevicepairing: MediaType =
      MediaType("application", "vnd.ms-windows.devicepairing", compressible = false, binary = true)

    lazy val vnddotmsWindowsdotnwprintingdotoob: MediaType =
      MediaType("application", "vnd.ms-windows.nwprinting.oob", compressible = false, binary = true)

    lazy val vnddotmsWindowsdotprinterpairing: MediaType =
      MediaType("application", "vnd.ms-windows.printerpairing", compressible = false, binary = true)

    lazy val vnddotmsWindowsdotwsddotoob: MediaType =
      MediaType("application", "vnd.ms-windows.wsd.oob", compressible = false, binary = true)

    lazy val vnddotmsWmdrmdotlicChlgReq: MediaType =
      MediaType("application", "vnd.ms-wmdrm.lic-chlg-req", compressible = false, binary = true)

    lazy val vnddotmsWmdrmdotlicResp: MediaType =
      MediaType("application", "vnd.ms-wmdrm.lic-resp", compressible = false, binary = true)

    lazy val vnddotmsWmdrmdotmeterChlgReq: MediaType =
      MediaType("application", "vnd.ms-wmdrm.meter-chlg-req", compressible = false, binary = true)

    lazy val vnddotmsWmdrmdotmeterResp: MediaType =
      MediaType("application", "vnd.ms-wmdrm.meter-resp", compressible = false, binary = true)

    lazy val vnddotmsWorddotdocumentdotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-word.document.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("docm")
      )

    lazy val vnddotmsWorddottemplatedotmacroenableddot12: MediaType =
      MediaType(
        "application",
        "vnd.ms-word.template.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("dotm")
      )

    lazy val vnddotmsWorks: MediaType =
      MediaType(
        "application",
        "vnd.ms-works",
        compressible = false,
        binary = true,
        fileExtensions = List("wps", "wks", "wcm", "wdb")
      )

    lazy val vnddotmsWpl: MediaType =
      MediaType("application", "vnd.ms-wpl", compressible = false, binary = true, fileExtensions = List("wpl"))

    lazy val vnddotmsXpsdocument: MediaType =
      MediaType("application", "vnd.ms-xpsdocument", compressible = false, binary = true, fileExtensions = List("xps"))

    lazy val vnddotmsaDiskImage: MediaType =
      MediaType("application", "vnd.msa-disk-image", compressible = false, binary = true)

    lazy val vnddotmseq: MediaType =
      MediaType("application", "vnd.mseq", compressible = false, binary = true, fileExtensions = List("mseq"))

    lazy val vnddotmsgpack: MediaType =
      MediaType("application", "vnd.msgpack", compressible = false, binary = true)

    lazy val vnddotmsign: MediaType =
      MediaType("application", "vnd.msign", compressible = false, binary = true)

    lazy val vnddotmultiaddotcreator: MediaType =
      MediaType("application", "vnd.multiad.creator", compressible = false, binary = true)

    lazy val vnddotmultiaddotcreatordotcif: MediaType =
      MediaType("application", "vnd.multiad.creator.cif", compressible = false, binary = true)

    lazy val vnddotmusicNiff: MediaType =
      MediaType("application", "vnd.music-niff", compressible = false, binary = true)

    lazy val vnddotmusician: MediaType =
      MediaType("application", "vnd.musician", compressible = false, binary = true, fileExtensions = List("mus"))

    lazy val vnddotmuveedotstyle: MediaType =
      MediaType("application", "vnd.muvee.style", compressible = false, binary = true, fileExtensions = List("msty"))

    lazy val vnddotmynfc: MediaType =
      MediaType("application", "vnd.mynfc", compressible = false, binary = true, fileExtensions = List("taglet"))

    lazy val vnddotnacamardotybridplusjson: MediaType =
      MediaType("application", "vnd.nacamar.ybrid+json", compressible = true, binary = false)

    lazy val vnddotnatodotbindingdataobjectpluscbor: MediaType =
      MediaType("application", "vnd.nato.bindingdataobject+cbor", compressible = false, binary = true)

    lazy val vnddotnatodotbindingdataobjectplusjson: MediaType =
      MediaType("application", "vnd.nato.bindingdataobject+json", compressible = true, binary = false)

    lazy val vnddotnatodotbindingdataobjectplusxml: MediaType =
      MediaType(
        "application",
        "vnd.nato.bindingdataobject+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("bdo")
      )

    lazy val vnddotnatodotopenxmlformatsPackagedotiepdpluszip: MediaType =
      MediaType("application", "vnd.nato.openxmlformats-package.iepd+zip", compressible = false, binary = true)

    lazy val vnddotncddotcontrol: MediaType =
      MediaType("application", "vnd.ncd.control", compressible = false, binary = true)

    lazy val vnddotncddotreference: MediaType =
      MediaType("application", "vnd.ncd.reference", compressible = false, binary = true)

    lazy val vnddotnearstdotinvplusjson: MediaType =
      MediaType("application", "vnd.nearst.inv+json", compressible = true, binary = false)

    lazy val vnddotnebuminddotline: MediaType =
      MediaType("application", "vnd.nebumind.line", compressible = false, binary = true)

    lazy val vnddotnervana: MediaType =
      MediaType("application", "vnd.nervana", compressible = false, binary = true)

    lazy val vnddotnetfpx: MediaType =
      MediaType("application", "vnd.netfpx", compressible = false, binary = true)

    lazy val vnddotneurolanguagedotnlu: MediaType =
      MediaType(
        "application",
        "vnd.neurolanguage.nlu",
        compressible = false,
        binary = true,
        fileExtensions = List("nlu")
      )

    lazy val vnddotnimn: MediaType =
      MediaType("application", "vnd.nimn", compressible = false, binary = true)

    lazy val vnddotnintendodotnitrodotrom: MediaType =
      MediaType("application", "vnd.nintendo.nitro.rom", compressible = false, binary = true)

    lazy val vnddotnintendodotsnesdotrom: MediaType =
      MediaType("application", "vnd.nintendo.snes.rom", compressible = false, binary = true)

    lazy val vnddotnitf: MediaType =
      MediaType("application", "vnd.nitf", compressible = false, binary = true, fileExtensions = List("ntf", "nitf"))

    lazy val vnddotnoblenetDirectory: MediaType =
      MediaType(
        "application",
        "vnd.noblenet-directory",
        compressible = false,
        binary = true,
        fileExtensions = List("nnd")
      )

    lazy val vnddotnoblenetSealer: MediaType =
      MediaType("application", "vnd.noblenet-sealer", compressible = false, binary = true, fileExtensions = List("nns"))

    lazy val vnddotnoblenetWeb: MediaType =
      MediaType("application", "vnd.noblenet-web", compressible = false, binary = true, fileExtensions = List("nnw"))

    lazy val vnddotnokiadotcatalogs: MediaType =
      MediaType("application", "vnd.nokia.catalogs", compressible = false, binary = true)

    lazy val vnddotnokiadotconmlpluswbxml: MediaType =
      MediaType("application", "vnd.nokia.conml+wbxml", compressible = false, binary = true)

    lazy val vnddotnokiadotconmlplusxml: MediaType =
      MediaType("application", "vnd.nokia.conml+xml", compressible = true, binary = true)

    lazy val vnddotnokiadotiptvdotconfigplusxml: MediaType =
      MediaType("application", "vnd.nokia.iptv.config+xml", compressible = true, binary = true)

    lazy val vnddotnokiadotisdsRadioPresets: MediaType =
      MediaType("application", "vnd.nokia.isds-radio-presets", compressible = false, binary = true)

    lazy val vnddotnokiadotlandmarkpluswbxml: MediaType =
      MediaType("application", "vnd.nokia.landmark+wbxml", compressible = false, binary = true)

    lazy val vnddotnokiadotlandmarkplusxml: MediaType =
      MediaType("application", "vnd.nokia.landmark+xml", compressible = true, binary = true)

    lazy val vnddotnokiadotlandmarkcollectionplusxml: MediaType =
      MediaType("application", "vnd.nokia.landmarkcollection+xml", compressible = true, binary = true)

    lazy val vnddotnokiadotnGagedotacplusxml: MediaType =
      MediaType(
        "application",
        "vnd.nokia.n-gage.ac+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("ac")
      )

    lazy val vnddotnokiadotnGagedotdata: MediaType =
      MediaType(
        "application",
        "vnd.nokia.n-gage.data",
        compressible = false,
        binary = true,
        fileExtensions = List("ngdat")
      )

    lazy val vnddotnokiadotnGagedotsymbiandotinstall: MediaType =
      MediaType(
        "application",
        "vnd.nokia.n-gage.symbian.install",
        compressible = false,
        binary = true,
        fileExtensions = List("n-gage")
      )

    lazy val vnddotnokiadotncd: MediaType =
      MediaType("application", "vnd.nokia.ncd", compressible = false, binary = true)

    lazy val vnddotnokiadotpcdpluswbxml: MediaType =
      MediaType("application", "vnd.nokia.pcd+wbxml", compressible = false, binary = true)

    lazy val vnddotnokiadotpcdplusxml: MediaType =
      MediaType("application", "vnd.nokia.pcd+xml", compressible = true, binary = true)

    lazy val vnddotnokiadotradioPreset: MediaType =
      MediaType(
        "application",
        "vnd.nokia.radio-preset",
        compressible = false,
        binary = true,
        fileExtensions = List("rpst")
      )

    lazy val vnddotnokiadotradioPresets: MediaType =
      MediaType(
        "application",
        "vnd.nokia.radio-presets",
        compressible = false,
        binary = true,
        fileExtensions = List("rpss")
      )

    lazy val vnddotnovadigmdotedm: MediaType =
      MediaType("application", "vnd.novadigm.edm", compressible = false, binary = true, fileExtensions = List("edm"))

    lazy val vnddotnovadigmdotedx: MediaType =
      MediaType("application", "vnd.novadigm.edx", compressible = false, binary = true, fileExtensions = List("edx"))

    lazy val vnddotnovadigmdotext: MediaType =
      MediaType("application", "vnd.novadigm.ext", compressible = false, binary = true, fileExtensions = List("ext"))

    lazy val vnddotnttLocaldotcontentShare: MediaType =
      MediaType("application", "vnd.ntt-local.content-share", compressible = false, binary = true)

    lazy val vnddotnttLocaldotfileTransfer: MediaType =
      MediaType("application", "vnd.ntt-local.file-transfer", compressible = false, binary = true)

    lazy val vnddotnttLocaldotogwRemoteAccess: MediaType =
      MediaType("application", "vnd.ntt-local.ogw_remote-access", compressible = false, binary = true)

    lazy val vnddotnttLocaldotsipTaRemote: MediaType =
      MediaType("application", "vnd.ntt-local.sip-ta_remote", compressible = false, binary = true)

    lazy val vnddotnttLocaldotsipTaTcpStream: MediaType =
      MediaType("application", "vnd.ntt-local.sip-ta_tcp_stream", compressible = false, binary = true)

    lazy val vnddotnubaltecdotnudokuGame: MediaType =
      MediaType("application", "vnd.nubaltec.nudoku-game", compressible = false, binary = true)

    lazy val vnddotoaidotworkflows: MediaType =
      MediaType("application", "vnd.oai.workflows", compressible = false, binary = true)

    lazy val vnddotoaidotworkflowsplusjson: MediaType =
      MediaType("application", "vnd.oai.workflows+json", compressible = true, binary = false)

    lazy val vnddotoaidotworkflowsplusyaml: MediaType =
      MediaType("application", "vnd.oai.workflows+yaml", compressible = false, binary = true)

    lazy val vnddotoasisdotopendocumentdotbase: MediaType =
      MediaType("application", "vnd.oasis.opendocument.base", compressible = false, binary = true)

    lazy val vnddotoasisdotopendocumentdotchart: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.chart",
        compressible = false,
        binary = true,
        fileExtensions = List("odc")
      )

    lazy val vnddotoasisdotopendocumentdotchartTemplate: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.chart-template",
        compressible = false,
        binary = true,
        fileExtensions = List("otc")
      )

    lazy val vnddotoasisdotopendocumentdotdatabase: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.database",
        compressible = false,
        binary = true,
        fileExtensions = List("odb")
      )

    lazy val vnddotoasisdotopendocumentdotformula: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.formula",
        compressible = false,
        binary = true,
        fileExtensions = List("odf")
      )

    lazy val vnddotoasisdotopendocumentdotformulaTemplate: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.formula-template",
        compressible = false,
        binary = true,
        fileExtensions = List("odft")
      )

    lazy val vnddotoasisdotopendocumentdotgraphics: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.graphics",
        compressible = false,
        binary = true,
        fileExtensions = List("odg")
      )

    lazy val vnddotoasisdotopendocumentdotgraphicsTemplate: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.graphics-template",
        compressible = false,
        binary = true,
        fileExtensions = List("otg")
      )

    lazy val vnddotoasisdotopendocumentdotimage: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.image",
        compressible = false,
        binary = true,
        fileExtensions = List("odi")
      )

    lazy val vnddotoasisdotopendocumentdotimageTemplate: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.image-template",
        compressible = false,
        binary = true,
        fileExtensions = List("oti")
      )

    lazy val vnddotoasisdotopendocumentdotpresentation: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.presentation",
        compressible = false,
        binary = true,
        fileExtensions = List("odp")
      )

    lazy val vnddotoasisdotopendocumentdotpresentationTemplate: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.presentation-template",
        compressible = false,
        binary = true,
        fileExtensions = List("otp")
      )

    lazy val vnddotoasisdotopendocumentdotspreadsheet: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.spreadsheet",
        compressible = false,
        binary = true,
        fileExtensions = List("ods")
      )

    lazy val vnddotoasisdotopendocumentdotspreadsheetTemplate: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.spreadsheet-template",
        compressible = false,
        binary = true,
        fileExtensions = List("ots")
      )

    lazy val vnddotoasisdotopendocumentdottext: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.text",
        compressible = false,
        binary = true,
        fileExtensions = List("odt")
      )

    lazy val vnddotoasisdotopendocumentdottextMaster: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.text-master",
        compressible = false,
        binary = true,
        fileExtensions = List("odm")
      )

    lazy val vnddotoasisdotopendocumentdottextMasterTemplate: MediaType =
      MediaType("application", "vnd.oasis.opendocument.text-master-template", compressible = false, binary = true)

    lazy val vnddotoasisdotopendocumentdottextTemplate: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.text-template",
        compressible = false,
        binary = true,
        fileExtensions = List("ott")
      )

    lazy val vnddotoasisdotopendocumentdottextWeb: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.text-web",
        compressible = false,
        binary = true,
        fileExtensions = List("oth")
      )

    lazy val vnddotobn: MediaType =
      MediaType("application", "vnd.obn", compressible = false, binary = true)

    lazy val vnddotocfpluscbor: MediaType =
      MediaType("application", "vnd.ocf+cbor", compressible = false, binary = true)

    lazy val vnddotocidotimagedotmanifestdotv1plusjson: MediaType =
      MediaType("application", "vnd.oci.image.manifest.v1+json", compressible = true, binary = false)

    lazy val vnddotoftndotl10nplusjson: MediaType =
      MediaType("application", "vnd.oftn.l10n+json", compressible = true, binary = false)

    lazy val vnddotoipfdotcontentaccessdownloadplusxml: MediaType =
      MediaType("application", "vnd.oipf.contentaccessdownload+xml", compressible = true, binary = true)

    lazy val vnddotoipfdotcontentaccessstreamingplusxml: MediaType =
      MediaType("application", "vnd.oipf.contentaccessstreaming+xml", compressible = true, binary = true)

    lazy val vnddotoipfdotcspgHexbinary: MediaType =
      MediaType("application", "vnd.oipf.cspg-hexbinary", compressible = false, binary = true)

    lazy val vnddotoipfdotdaedotsvgplusxml: MediaType =
      MediaType("application", "vnd.oipf.dae.svg+xml", compressible = true, binary = true)

    lazy val vnddotoipfdotdaedotxhtmlplusxml: MediaType =
      MediaType("application", "vnd.oipf.dae.xhtml+xml", compressible = true, binary = true)

    lazy val vnddotoipfdotmippvcontrolmessageplusxml: MediaType =
      MediaType("application", "vnd.oipf.mippvcontrolmessage+xml", compressible = true, binary = true)

    lazy val vnddotoipfdotpaedotgem: MediaType =
      MediaType("application", "vnd.oipf.pae.gem", compressible = false, binary = true)

    lazy val vnddotoipfdotspdiscoveryplusxml: MediaType =
      MediaType("application", "vnd.oipf.spdiscovery+xml", compressible = true, binary = true)

    lazy val vnddotoipfdotspdlistplusxml: MediaType =
      MediaType("application", "vnd.oipf.spdlist+xml", compressible = true, binary = true)

    lazy val vnddotoipfdotueprofileplusxml: MediaType =
      MediaType("application", "vnd.oipf.ueprofile+xml", compressible = true, binary = true)

    lazy val vnddotoipfdotuserprofileplusxml: MediaType =
      MediaType("application", "vnd.oipf.userprofile+xml", compressible = true, binary = true)

    lazy val vnddotolpcSugar: MediaType =
      MediaType("application", "vnd.olpc-sugar", compressible = false, binary = true, fileExtensions = List("xo"))

    lazy val vnddotomaScwsConfig: MediaType =
      MediaType("application", "vnd.oma-scws-config", compressible = false, binary = true)

    lazy val vnddotomaScwsHttpRequest: MediaType =
      MediaType("application", "vnd.oma-scws-http-request", compressible = false, binary = true)

    lazy val vnddotomaScwsHttpResponse: MediaType =
      MediaType("application", "vnd.oma-scws-http-response", compressible = false, binary = true)

    lazy val vnddotomadotbcastdotassociatedProcedureParameterplusxml: MediaType =
      MediaType("application", "vnd.oma.bcast.associated-procedure-parameter+xml", compressible = true, binary = true)

    lazy val vnddotomadotbcastdotdrmTriggerplusxml: MediaType =
      MediaType("application", "vnd.oma.bcast.drm-trigger+xml", compressible = true, binary = true)

    lazy val vnddotomadotbcastdotimdplusxml: MediaType =
      MediaType("application", "vnd.oma.bcast.imd+xml", compressible = true, binary = true)

    lazy val vnddotomadotbcastdotltkm: MediaType =
      MediaType("application", "vnd.oma.bcast.ltkm", compressible = false, binary = true)

    lazy val vnddotomadotbcastdotnotificationplusxml: MediaType =
      MediaType("application", "vnd.oma.bcast.notification+xml", compressible = true, binary = true)

    lazy val vnddotomadotbcastdotprovisioningtrigger: MediaType =
      MediaType("application", "vnd.oma.bcast.provisioningtrigger", compressible = false, binary = true)

    lazy val vnddotomadotbcastdotsgboot: MediaType =
      MediaType("application", "vnd.oma.bcast.sgboot", compressible = false, binary = true)

    lazy val vnddotomadotbcastdotsgddplusxml: MediaType =
      MediaType("application", "vnd.oma.bcast.sgdd+xml", compressible = true, binary = true)

    lazy val vnddotomadotbcastdotsgdu: MediaType =
      MediaType("application", "vnd.oma.bcast.sgdu", compressible = false, binary = true)

    lazy val vnddotomadotbcastdotsimpleSymbolContainer: MediaType =
      MediaType("application", "vnd.oma.bcast.simple-symbol-container", compressible = false, binary = true)

    lazy val vnddotomadotbcastdotsmartcardTriggerplusxml: MediaType =
      MediaType("application", "vnd.oma.bcast.smartcard-trigger+xml", compressible = true, binary = true)

    lazy val vnddotomadotbcastdotsprovplusxml: MediaType =
      MediaType("application", "vnd.oma.bcast.sprov+xml", compressible = true, binary = true)

    lazy val vnddotomadotbcastdotstkm: MediaType =
      MediaType("application", "vnd.oma.bcast.stkm", compressible = false, binary = true)

    lazy val vnddotomadotcabAddressBookplusxml: MediaType =
      MediaType("application", "vnd.oma.cab-address-book+xml", compressible = true, binary = true)

    lazy val vnddotomadotcabFeatureHandlerplusxml: MediaType =
      MediaType("application", "vnd.oma.cab-feature-handler+xml", compressible = true, binary = true)

    lazy val vnddotomadotcabPccplusxml: MediaType =
      MediaType("application", "vnd.oma.cab-pcc+xml", compressible = true, binary = true)

    lazy val vnddotomadotcabSubsInviteplusxml: MediaType =
      MediaType("application", "vnd.oma.cab-subs-invite+xml", compressible = true, binary = true)

    lazy val vnddotomadotcabUserPrefsplusxml: MediaType =
      MediaType("application", "vnd.oma.cab-user-prefs+xml", compressible = true, binary = true)

    lazy val vnddotomadotdcd: MediaType =
      MediaType("application", "vnd.oma.dcd", compressible = false, binary = true)

    lazy val vnddotomadotdcdc: MediaType =
      MediaType("application", "vnd.oma.dcdc", compressible = false, binary = true)

    lazy val vnddotomadotdd2plusxml: MediaType =
      MediaType("application", "vnd.oma.dd2+xml", compressible = true, binary = true, fileExtensions = List("dd2"))

    lazy val vnddotomadotdrmdotrisdplusxml: MediaType =
      MediaType("application", "vnd.oma.drm.risd+xml", compressible = true, binary = true)

    lazy val vnddotomadotgroupUsageListplusxml: MediaType =
      MediaType("application", "vnd.oma.group-usage-list+xml", compressible = true, binary = true)

    lazy val vnddotomadotlwm2mpluscbor: MediaType =
      MediaType("application", "vnd.oma.lwm2m+cbor", compressible = false, binary = true)

    lazy val vnddotomadotlwm2mplusjson: MediaType =
      MediaType("application", "vnd.oma.lwm2m+json", compressible = true, binary = false)

    lazy val vnddotomadotlwm2mplustlv: MediaType =
      MediaType("application", "vnd.oma.lwm2m+tlv", compressible = false, binary = true)

    lazy val vnddotomadotpalplusxml: MediaType =
      MediaType("application", "vnd.oma.pal+xml", compressible = true, binary = true)

    lazy val vnddotomadotpocdotdetailedProgressReportplusxml: MediaType =
      MediaType("application", "vnd.oma.poc.detailed-progress-report+xml", compressible = true, binary = true)

    lazy val vnddotomadotpocdotfinalReportplusxml: MediaType =
      MediaType("application", "vnd.oma.poc.final-report+xml", compressible = true, binary = true)

    lazy val vnddotomadotpocdotgroupsplusxml: MediaType =
      MediaType("application", "vnd.oma.poc.groups+xml", compressible = true, binary = true)

    lazy val vnddotomadotpocdotinvocationDescriptorplusxml: MediaType =
      MediaType("application", "vnd.oma.poc.invocation-descriptor+xml", compressible = true, binary = true)

    lazy val vnddotomadotpocdotoptimizedProgressReportplusxml: MediaType =
      MediaType("application", "vnd.oma.poc.optimized-progress-report+xml", compressible = true, binary = true)

    lazy val vnddotomadotpush: MediaType =
      MediaType("application", "vnd.oma.push", compressible = false, binary = true)

    lazy val vnddotomadotscidmdotmessagesplusxml: MediaType =
      MediaType("application", "vnd.oma.scidm.messages+xml", compressible = true, binary = true)

    lazy val vnddotomadotxcapDirectoryplusxml: MediaType =
      MediaType("application", "vnd.oma.xcap-directory+xml", compressible = true, binary = true)

    lazy val vnddotomadsEmailplusxml: MediaType =
      MediaType("application", "vnd.omads-email+xml", compressible = true, binary = true)

    lazy val vnddotomadsFileplusxml: MediaType =
      MediaType("application", "vnd.omads-file+xml", compressible = true, binary = true)

    lazy val vnddotomadsFolderplusxml: MediaType =
      MediaType("application", "vnd.omads-folder+xml", compressible = true, binary = true)

    lazy val vnddotomalocSuplInit: MediaType =
      MediaType("application", "vnd.omaloc-supl-init", compressible = false, binary = true)

    lazy val vnddotomsdotcellularCoseContentpluscbor: MediaType =
      MediaType("application", "vnd.oms.cellular-cose-content+cbor", compressible = false, binary = true)

    lazy val vnddotonepager: MediaType =
      MediaType("application", "vnd.onepager", compressible = false, binary = true)

    lazy val vnddotonepagertamp: MediaType =
      MediaType("application", "vnd.onepagertamp", compressible = false, binary = true)

    lazy val vnddotonepagertamx: MediaType =
      MediaType("application", "vnd.onepagertamx", compressible = false, binary = true)

    lazy val vnddotonepagertat: MediaType =
      MediaType("application", "vnd.onepagertat", compressible = false, binary = true)

    lazy val vnddotonepagertatp: MediaType =
      MediaType("application", "vnd.onepagertatp", compressible = false, binary = true)

    lazy val vnddotonepagertatx: MediaType =
      MediaType("application", "vnd.onepagertatx", compressible = false, binary = true)

    lazy val vnddotonvifdotmetadata: MediaType =
      MediaType("application", "vnd.onvif.metadata", compressible = false, binary = true)

    lazy val vnddotopenbloxdotgameplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openblox.game+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("obgx")
      )

    lazy val vnddotopenbloxdotgameBinary: MediaType =
      MediaType("application", "vnd.openblox.game-binary", compressible = false, binary = true)

    lazy val vnddotopeneyedotoeb: MediaType =
      MediaType("application", "vnd.openeye.oeb", compressible = false, binary = true)

    lazy val vnddotopenofficeorgdotextension: MediaType =
      MediaType(
        "application",
        "vnd.openofficeorg.extension",
        compressible = false,
        binary = true,
        fileExtensions = List("oxt")
      )

    lazy val vnddotopenprinttag: MediaType =
      MediaType("application", "vnd.openprinttag", compressible = false, binary = true)

    lazy val vnddotopenstreetmapdotdataplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openstreetmap.data+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("osm")
      )

    lazy val vnddotopentimestampsdotots: MediaType =
      MediaType("application", "vnd.opentimestamps.ots", compressible = false, binary = true)

    lazy val vnddotopenvpidotdspxplusjson: MediaType =
      MediaType("application", "vnd.openvpi.dspx+json", compressible = true, binary = false)

    lazy val vnddotopenxmlformatsOfficedocumentdotcustomPropertiesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.custom-properties+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotcustomxmlpropertiesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.customxmlproperties+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotdrawingplusxml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.drawing+xml", compressible = true, binary = true)

    lazy val vnddotopenxmlformatsOfficedocumentdotdrawingmldotchartplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.chart+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotdrawingmldotchartshapesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.chartshapes+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotdrawingmldotdiagramcolorsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotdrawingmldotdiagramdataplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.diagramdata+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotdrawingmldotdiagramlayoutplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotdrawingmldotdiagramstyleplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotextendedPropertiesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.extended-properties+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotcommentauthorsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.commentauthors+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotcommentsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.comments+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldothandoutmasterplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotnotesmasterplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.notesmaster+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotnotesslideplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.notesslide+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotpresentation: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.presentation",
        compressible = false,
        binary = true,
        fileExtensions = List("pptx")
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotpresentationdotmainplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotprespropsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.presprops+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotslide: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slide",
        compressible = false,
        binary = true,
        fileExtensions = List("sldx")
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotslideplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slide+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotslidelayoutplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slidelayout+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotslidemasterplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slidemaster+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotslideshow: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slideshow",
        compressible = false,
        binary = true,
        fileExtensions = List("ppsx")
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotslideshowdotmainplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotslideupdateinfoplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldottablestylesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.tablestyles+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldottagsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.tags+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldottemplate: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.template",
        compressible = false,
        binary = true,
        fileExtensions = List("potx")
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldottemplatedotmainplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.template.main+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotpresentationmldotviewpropsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.viewprops+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotcalcchainplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotchartsheetplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotcommentsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.comments+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotconnectionsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.connections+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotdialogsheetplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotexternallinkplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotpivotcachedefinitionplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotpivotcacherecordsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotpivottableplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotquerytableplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotrevisionheadersplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotrevisionlogplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotsharedstringsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotsheet: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        compressible = false,
        binary = true,
        fileExtensions = List("xlsx")
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotsheetdotmainplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotsheetmetadataplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotstylesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.styles+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldottableplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.table+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldottablesinglecellsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldottemplate: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.template",
        compressible = false,
        binary = true,
        fileExtensions = List("xltx")
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldottemplatedotmainplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotusernamesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotvolatiledependenciesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotworksheetplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotthemeplusxml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.theme+xml", compressible = true, binary = true)

    lazy val vnddotopenxmlformatsOfficedocumentdotthemeoverrideplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.themeoverride+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotvmldrawing: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.vmldrawing", compressible = false, binary = true)

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotcommentsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.comments+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotdocument: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.document",
        compressible = false,
        binary = true,
        fileExtensions = List("docx")
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotdocumentdotglossaryplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotdocumentdotmainplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotendnotesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotfonttableplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotfooterplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.footer+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotfootnotesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotnumberingplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotsettingsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.settings+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotstylesplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.styles+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldottemplate: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.template",
        compressible = false,
        binary = true,
        fileExtensions = List("dotx")
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldottemplatedotmainplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotwebsettingsplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsPackagedotcorePropertiesplusxml: MediaType =
      MediaType("application", "vnd.openxmlformats-package.core-properties+xml", compressible = true, binary = true)

    lazy val vnddotopenxmlformatsPackagedotdigitalSignatureXmlsignatureplusxml: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-package.digital-signature-xmlsignature+xml",
        compressible = true,
        binary = true
      )

    lazy val vnddotopenxmlformatsPackagedotrelationshipsplusxml: MediaType =
      MediaType("application", "vnd.openxmlformats-package.relationships+xml", compressible = true, binary = true)

    lazy val vnddotoracledotresourceplusjson: MediaType =
      MediaType("application", "vnd.oracle.resource+json", compressible = true, binary = false)

    lazy val vnddotorangedotindata: MediaType =
      MediaType("application", "vnd.orange.indata", compressible = false, binary = true)

    lazy val vnddotosadotnetdeploy: MediaType =
      MediaType("application", "vnd.osa.netdeploy", compressible = false, binary = true)

    lazy val vnddotosgeodotmapguidedotpackage: MediaType =
      MediaType(
        "application",
        "vnd.osgeo.mapguide.package",
        compressible = false,
        binary = true,
        fileExtensions = List("mgp")
      )

    lazy val vnddotosgidotbundle: MediaType =
      MediaType("application", "vnd.osgi.bundle", compressible = false, binary = true)

    lazy val vnddotosgidotdp: MediaType =
      MediaType("application", "vnd.osgi.dp", compressible = false, binary = true, fileExtensions = List("dp"))

    lazy val vnddotosgidotsubsystem: MediaType =
      MediaType("application", "vnd.osgi.subsystem", compressible = false, binary = true, fileExtensions = List("esa"))

    lazy val vnddototpsdotctKipplusxml: MediaType =
      MediaType("application", "vnd.otps.ct-kip+xml", compressible = true, binary = true)

    lazy val vnddotoxlidotcountgraph: MediaType =
      MediaType("application", "vnd.oxli.countgraph", compressible = false, binary = true)

    lazy val vnddotpagerdutyplusjson: MediaType =
      MediaType("application", "vnd.pagerduty+json", compressible = true, binary = false)

    lazy val vnddotpalm: MediaType =
      MediaType(
        "application",
        "vnd.palm",
        compressible = false,
        binary = true,
        fileExtensions = List("pdb", "pqa", "oprc")
      )

    lazy val vnddotpanoply: MediaType =
      MediaType("application", "vnd.panoply", compressible = false, binary = true)

    lazy val vnddotpaosdotxml: MediaType =
      MediaType("application", "vnd.paos.xml", compressible = false, binary = true)

    lazy val vnddotpatentdive: MediaType =
      MediaType("application", "vnd.patentdive", compressible = false, binary = true)

    lazy val vnddotpatientecommsdoc: MediaType =
      MediaType("application", "vnd.patientecommsdoc", compressible = false, binary = true)

    lazy val vnddotpawaafile: MediaType =
      MediaType("application", "vnd.pawaafile", compressible = false, binary = true, fileExtensions = List("paw"))

    lazy val vnddotpcos: MediaType =
      MediaType("application", "vnd.pcos", compressible = false, binary = true)

    lazy val vnddotpgdotformat: MediaType =
      MediaType("application", "vnd.pg.format", compressible = false, binary = true, fileExtensions = List("str"))

    lazy val vnddotpgdotosasli: MediaType =
      MediaType("application", "vnd.pg.osasli", compressible = false, binary = true, fileExtensions = List("ei6"))

    lazy val vnddotpiaccessdotapplicationLicence: MediaType =
      MediaType("application", "vnd.piaccess.application-licence", compressible = false, binary = true)

    lazy val vnddotpicsel: MediaType =
      MediaType("application", "vnd.picsel", compressible = false, binary = true, fileExtensions = List("efif"))

    lazy val vnddotpmidotwidget: MediaType =
      MediaType("application", "vnd.pmi.widget", compressible = false, binary = true, fileExtensions = List("wg"))

    lazy val vnddotpmtiles: MediaType =
      MediaType("application", "vnd.pmtiles", compressible = false, binary = true)

    lazy val vnddotpocdotgroupAdvertisementplusxml: MediaType =
      MediaType("application", "vnd.poc.group-advertisement+xml", compressible = true, binary = true)

    lazy val vnddotpocketlearn: MediaType =
      MediaType("application", "vnd.pocketlearn", compressible = false, binary = true, fileExtensions = List("plf"))

    lazy val vnddotpowerbuilder6: MediaType =
      MediaType("application", "vnd.powerbuilder6", compressible = false, binary = true, fileExtensions = List("pbd"))

    lazy val vnddotpowerbuilder6S: MediaType =
      MediaType("application", "vnd.powerbuilder6-s", compressible = false, binary = true)

    lazy val vnddotpowerbuilder7: MediaType =
      MediaType("application", "vnd.powerbuilder7", compressible = false, binary = true)

    lazy val vnddotpowerbuilder7S: MediaType =
      MediaType("application", "vnd.powerbuilder7-s", compressible = false, binary = true)

    lazy val vnddotpowerbuilder75: MediaType =
      MediaType("application", "vnd.powerbuilder75", compressible = false, binary = true)

    lazy val vnddotpowerbuilder75S: MediaType =
      MediaType("application", "vnd.powerbuilder75-s", compressible = false, binary = true)

    lazy val vnddotppdotsystemverifyplusxml: MediaType =
      MediaType(
        "application",
        "vnd.pp.systemverify+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("systemverify")
      )

    lazy val vnddotpreminet: MediaType =
      MediaType("application", "vnd.preminet", compressible = false, binary = true)

    lazy val vnddotpreviewsystemsdotbox: MediaType =
      MediaType(
        "application",
        "vnd.previewsystems.box",
        compressible = false,
        binary = true,
        fileExtensions = List("box")
      )

    lazy val vnddotprocreatedotbrush: MediaType =
      MediaType(
        "application",
        "vnd.procreate.brush",
        compressible = false,
        binary = true,
        fileExtensions = List("brush")
      )

    lazy val vnddotprocreatedotbrushset: MediaType =
      MediaType(
        "application",
        "vnd.procreate.brushset",
        compressible = false,
        binary = true,
        fileExtensions = List("brushset")
      )

    lazy val vnddotprocreatedotdream: MediaType =
      MediaType("application", "vnd.procreate.dream", compressible = false, binary = true, fileExtensions = List("drm"))

    lazy val vnddotprojectGraph: MediaType =
      MediaType("application", "vnd.project-graph", compressible = false, binary = true)

    lazy val vnddotproteusdotmagazine: MediaType =
      MediaType(
        "application",
        "vnd.proteus.magazine",
        compressible = false,
        binary = true,
        fileExtensions = List("mgz")
      )

    lazy val vnddotpsfs: MediaType =
      MediaType("application", "vnd.psfs", compressible = false, binary = true)

    lazy val vnddotptdotmundusmundi: MediaType =
      MediaType("application", "vnd.pt.mundusmundi", compressible = false, binary = true)

    lazy val vnddotpublishareDeltaTree: MediaType =
      MediaType(
        "application",
        "vnd.publishare-delta-tree",
        compressible = false,
        binary = true,
        fileExtensions = List("qps")
      )

    lazy val vnddotpvidotptid1: MediaType =
      MediaType("application", "vnd.pvi.ptid1", compressible = false, binary = true, fileExtensions = List("ptid"))

    lazy val vnddotpwgMultiplexed: MediaType =
      MediaType("application", "vnd.pwg-multiplexed", compressible = false, binary = true)

    lazy val vnddotpwgXhtmlPrintplusxml: MediaType =
      MediaType(
        "application",
        "vnd.pwg-xhtml-print+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("xhtm")
      )

    lazy val vnddotpyonplusjson: MediaType =
      MediaType("application", "vnd.pyon+json", compressible = true, binary = false)

    lazy val vnddotqualcommdotbrewAppRes: MediaType =
      MediaType("application", "vnd.qualcomm.brew-app-res", compressible = false, binary = true)

    lazy val vnddotquarantainenet: MediaType =
      MediaType("application", "vnd.quarantainenet", compressible = false, binary = true)

    lazy val vnddotquarkdotquarkxpress: MediaType =
      MediaType(
        "application",
        "vnd.quark.quarkxpress",
        compressible = false,
        binary = true,
        fileExtensions = List("qxd", "qxt", "qwd", "qwt", "qxl", "qxb")
      )

    lazy val vnddotquobjectQuoxdocument: MediaType =
      MediaType("application", "vnd.quobject-quoxdocument", compressible = false, binary = true)

    lazy val vnddotr74ndotsandboxelsplusjson: MediaType =
      MediaType("application", "vnd.r74n.sandboxels+json", compressible = true, binary = false)

    lazy val vnddotradisysdotmomlplusxml: MediaType =
      MediaType("application", "vnd.radisys.moml+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlAuditplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlAuditConfplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-conf+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlAuditConnplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-conn+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlAuditDialogplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-dialog+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlAuditStreamplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-stream+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlConfplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-conf+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlDialogplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlDialogBaseplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-base+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlDialogFaxDetectplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-fax-detect+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlDialogFaxSendrecvplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-fax-sendrecv+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlDialogGroupplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-group+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlDialogSpeechplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-speech+xml", compressible = true, binary = true)

    lazy val vnddotradisysdotmsmlDialogTransformplusxml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-transform+xml", compressible = true, binary = true)

    lazy val vnddotrainstordotdata: MediaType =
      MediaType("application", "vnd.rainstor.data", compressible = false, binary = true)

    lazy val vnddotrapid: MediaType =
      MediaType("application", "vnd.rapid", compressible = false, binary = true)

    lazy val vnddotrar: MediaType =
      MediaType("application", "vnd.rar", compressible = false, binary = true, fileExtensions = List("rar"))

    lazy val vnddotrealvncdotbed: MediaType =
      MediaType("application", "vnd.realvnc.bed", compressible = false, binary = true, fileExtensions = List("bed"))

    lazy val vnddotrecordaredotmusicxml: MediaType =
      MediaType(
        "application",
        "vnd.recordare.musicxml",
        compressible = false,
        binary = true,
        fileExtensions = List("mxl")
      )

    lazy val vnddotrecordaredotmusicxmlplusxml: MediaType =
      MediaType(
        "application",
        "vnd.recordare.musicxml+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("musicxml")
      )

    lazy val vnddotrelpipe: MediaType =
      MediaType("application", "vnd.relpipe", compressible = false, binary = true)

    lazy val vnddotrenlearndotrlprint: MediaType =
      MediaType("application", "vnd.renlearn.rlprint", compressible = false, binary = true)

    lazy val vnddotresilientdotlogic: MediaType =
      MediaType("application", "vnd.resilient.logic", compressible = false, binary = true)

    lazy val vnddotrestfulplusjson: MediaType =
      MediaType("application", "vnd.restful+json", compressible = true, binary = false)

    lazy val vnddotrigdotcryptonote: MediaType =
      MediaType(
        "application",
        "vnd.rig.cryptonote",
        compressible = false,
        binary = true,
        fileExtensions = List("cryptonote")
      )

    lazy val vnddotrimdotcod: MediaType =
      MediaType("application", "vnd.rim.cod", compressible = false, binary = true, fileExtensions = List("cod"))

    lazy val vnddotrnRealmedia: MediaType =
      MediaType("application", "vnd.rn-realmedia", compressible = false, binary = true, fileExtensions = List("rm"))

    lazy val vnddotrnRealmediaVbr: MediaType =
      MediaType(
        "application",
        "vnd.rn-realmedia-vbr",
        compressible = false,
        binary = true,
        fileExtensions = List("rmvb")
      )

    lazy val vnddotroute66dotlink66plusxml: MediaType =
      MediaType(
        "application",
        "vnd.route66.link66+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("link66")
      )

    lazy val vnddotrs274x: MediaType =
      MediaType("application", "vnd.rs-274x", compressible = false, binary = true)

    lazy val vnddotruckusdotdownload: MediaType =
      MediaType("application", "vnd.ruckus.download", compressible = false, binary = true)

    lazy val vnddots3sms: MediaType =
      MediaType("application", "vnd.s3sms", compressible = false, binary = true)

    lazy val vnddotsailingtrackerdottrack: MediaType =
      MediaType(
        "application",
        "vnd.sailingtracker.track",
        compressible = false,
        binary = true,
        fileExtensions = List("st")
      )

    lazy val vnddotsar: MediaType =
      MediaType("application", "vnd.sar", compressible = false, binary = true)

    lazy val vnddotsbmdotcid: MediaType =
      MediaType("application", "vnd.sbm.cid", compressible = false, binary = true)

    lazy val vnddotsbmdotmid2: MediaType =
      MediaType("application", "vnd.sbm.mid2", compressible = false, binary = true)

    lazy val vnddotscribus: MediaType =
      MediaType("application", "vnd.scribus", compressible = false, binary = true)

    lazy val vnddotsealeddot3df: MediaType =
      MediaType("application", "vnd.sealed.3df", compressible = false, binary = true)

    lazy val vnddotsealeddotcsf: MediaType =
      MediaType("application", "vnd.sealed.csf", compressible = false, binary = true)

    lazy val vnddotsealeddotdoc: MediaType =
      MediaType("application", "vnd.sealed.doc", compressible = false, binary = true)

    lazy val vnddotsealeddoteml: MediaType =
      MediaType("application", "vnd.sealed.eml", compressible = false, binary = true)

    lazy val vnddotsealeddotmht: MediaType =
      MediaType("application", "vnd.sealed.mht", compressible = false, binary = true)

    lazy val vnddotsealeddotnet: MediaType =
      MediaType("application", "vnd.sealed.net", compressible = false, binary = true)

    lazy val vnddotsealeddotppt: MediaType =
      MediaType("application", "vnd.sealed.ppt", compressible = false, binary = true)

    lazy val vnddotsealeddottiff: MediaType =
      MediaType("application", "vnd.sealed.tiff", compressible = false, binary = true)

    lazy val vnddotsealeddotxls: MediaType =
      MediaType("application", "vnd.sealed.xls", compressible = false, binary = true)

    lazy val vnddotsealedmediadotsoftsealdothtml: MediaType =
      MediaType("application", "vnd.sealedmedia.softseal.html", compressible = false, binary = true)

    lazy val vnddotsealedmediadotsoftsealdotpdf: MediaType =
      MediaType("application", "vnd.sealedmedia.softseal.pdf", compressible = false, binary = true)

    lazy val vnddotseemail: MediaType =
      MediaType("application", "vnd.seemail", compressible = false, binary = true, fileExtensions = List("see"))

    lazy val vnddotseisplusjson: MediaType =
      MediaType("application", "vnd.seis+json", compressible = true, binary = false)

    lazy val vnddotsema: MediaType =
      MediaType("application", "vnd.sema", compressible = false, binary = true, fileExtensions = List("sema"))

    lazy val vnddotsemd: MediaType =
      MediaType("application", "vnd.semd", compressible = false, binary = true, fileExtensions = List("semd"))

    lazy val vnddotsemf: MediaType =
      MediaType("application", "vnd.semf", compressible = false, binary = true, fileExtensions = List("semf"))

    lazy val vnddotshadeSaveFile: MediaType =
      MediaType("application", "vnd.shade-save-file", compressible = false, binary = true)

    lazy val vnddotshanadotinformeddotformdata: MediaType =
      MediaType(
        "application",
        "vnd.shana.informed.formdata",
        compressible = false,
        binary = true,
        fileExtensions = List("ifm")
      )

    lazy val vnddotshanadotinformeddotformtemplate: MediaType =
      MediaType(
        "application",
        "vnd.shana.informed.formtemplate",
        compressible = false,
        binary = true,
        fileExtensions = List("itp")
      )

    lazy val vnddotshanadotinformeddotinterchange: MediaType =
      MediaType(
        "application",
        "vnd.shana.informed.interchange",
        compressible = false,
        binary = true,
        fileExtensions = List("iif")
      )

    lazy val vnddotshanadotinformeddotpackage: MediaType =
      MediaType(
        "application",
        "vnd.shana.informed.package",
        compressible = false,
        binary = true,
        fileExtensions = List("ipk")
      )

    lazy val vnddotshootproofplusjson: MediaType =
      MediaType("application", "vnd.shootproof+json", compressible = true, binary = false)

    lazy val vnddotshopkickplusjson: MediaType =
      MediaType("application", "vnd.shopkick+json", compressible = true, binary = false)

    lazy val vnddotshp: MediaType =
      MediaType("application", "vnd.shp", compressible = false, binary = true)

    lazy val vnddotshx: MediaType =
      MediaType("application", "vnd.shx", compressible = false, binary = true)

    lazy val vnddotsigrokdotsession: MediaType =
      MediaType("application", "vnd.sigrok.session", compressible = false, binary = true)

    lazy val vnddotsimtechMindmapper: MediaType =
      MediaType(
        "application",
        "vnd.simtech-mindmapper",
        compressible = false,
        binary = true,
        fileExtensions = List("twd", "twds")
      )

    lazy val vnddotsirenplusjson: MediaType =
      MediaType("application", "vnd.siren+json", compressible = true, binary = false)

    lazy val vnddotsirtxdotvmv0: MediaType =
      MediaType("application", "vnd.sirtx.vmv0", compressible = false, binary = true)

    lazy val vnddotsketchometry: MediaType =
      MediaType("application", "vnd.sketchometry", compressible = false, binary = true)

    lazy val vnddotsmaf: MediaType =
      MediaType("application", "vnd.smaf", compressible = false, binary = true, fileExtensions = List("mmf"))

    lazy val vnddotsmartdotnotebook: MediaType =
      MediaType("application", "vnd.smart.notebook", compressible = false, binary = true)

    lazy val vnddotsmartdotteacher: MediaType =
      MediaType(
        "application",
        "vnd.smart.teacher",
        compressible = false,
        binary = true,
        fileExtensions = List("teacher")
      )

    lazy val vnddotsmintiodotportalsdotarchive: MediaType =
      MediaType("application", "vnd.smintio.portals.archive", compressible = false, binary = true)

    lazy val vnddotsnesdevPageTable: MediaType =
      MediaType("application", "vnd.snesdev-page-table", compressible = false, binary = true)

    lazy val vnddotsoftware602dotfillerdotformplusxml: MediaType =
      MediaType(
        "application",
        "vnd.software602.filler.form+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("fo")
      )

    lazy val vnddotsoftware602dotfillerdotformXmlZip: MediaType =
      MediaType("application", "vnd.software602.filler.form-xml-zip", compressible = false, binary = true)

    lazy val vnddotsolentdotsdkmplusxml: MediaType =
      MediaType(
        "application",
        "vnd.solent.sdkm+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("sdkm", "sdkd")
      )

    lazy val vnddotspotfiredotdxp: MediaType =
      MediaType("application", "vnd.spotfire.dxp", compressible = false, binary = true, fileExtensions = List("dxp"))

    lazy val vnddotspotfiredotsfs: MediaType =
      MediaType("application", "vnd.spotfire.sfs", compressible = false, binary = true, fileExtensions = List("sfs"))

    lazy val vnddotsqlite3: MediaType =
      MediaType(
        "application",
        "vnd.sqlite3",
        compressible = false,
        binary = true,
        fileExtensions = List("sqlite", "sqlite3")
      )

    lazy val vnddotsssCod: MediaType =
      MediaType("application", "vnd.sss-cod", compressible = false, binary = true)

    lazy val vnddotsssDtf: MediaType =
      MediaType("application", "vnd.sss-dtf", compressible = false, binary = true)

    lazy val vnddotsssNtf: MediaType =
      MediaType("application", "vnd.sss-ntf", compressible = false, binary = true)

    lazy val vnddotstardivisiondotcalc: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.calc",
        compressible = false,
        binary = true,
        fileExtensions = List("sdc")
      )

    lazy val vnddotstardivisiondotdraw: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.draw",
        compressible = false,
        binary = true,
        fileExtensions = List("sda")
      )

    lazy val vnddotstardivisiondotimpress: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.impress",
        compressible = false,
        binary = true,
        fileExtensions = List("sdd")
      )

    lazy val vnddotstardivisiondotmath: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.math",
        compressible = false,
        binary = true,
        fileExtensions = List("smf")
      )

    lazy val vnddotstardivisiondotwriter: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.writer",
        compressible = false,
        binary = true,
        fileExtensions = List("sdw", "vor")
      )

    lazy val vnddotstardivisiondotwriterGlobal: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.writer-global",
        compressible = false,
        binary = true,
        fileExtensions = List("sgl")
      )

    lazy val vnddotstepmaniadotpackage: MediaType =
      MediaType(
        "application",
        "vnd.stepmania.package",
        compressible = false,
        binary = true,
        fileExtensions = List("smzip")
      )

    lazy val vnddotstepmaniadotstepchart: MediaType =
      MediaType(
        "application",
        "vnd.stepmania.stepchart",
        compressible = false,
        binary = true,
        fileExtensions = List("sm")
      )

    lazy val vnddotstreetStream: MediaType =
      MediaType("application", "vnd.street-stream", compressible = false, binary = true)

    lazy val vnddotsundotwadlplusxml: MediaType =
      MediaType("application", "vnd.sun.wadl+xml", compressible = true, binary = true, fileExtensions = List("wadl"))

    lazy val vnddotsundotxmldotcalc: MediaType =
      MediaType("application", "vnd.sun.xml.calc", compressible = false, binary = true, fileExtensions = List("sxc"))

    lazy val vnddotsundotxmldotcalcdottemplate: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.calc.template",
        compressible = false,
        binary = true,
        fileExtensions = List("stc")
      )

    lazy val vnddotsundotxmldotdraw: MediaType =
      MediaType("application", "vnd.sun.xml.draw", compressible = false, binary = true, fileExtensions = List("sxd"))

    lazy val vnddotsundotxmldotdrawdottemplate: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.draw.template",
        compressible = false,
        binary = true,
        fileExtensions = List("std")
      )

    lazy val vnddotsundotxmldotimpress: MediaType =
      MediaType("application", "vnd.sun.xml.impress", compressible = false, binary = true, fileExtensions = List("sxi"))

    lazy val vnddotsundotxmldotimpressdottemplate: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.impress.template",
        compressible = false,
        binary = true,
        fileExtensions = List("sti")
      )

    lazy val vnddotsundotxmldotmath: MediaType =
      MediaType("application", "vnd.sun.xml.math", compressible = false, binary = true, fileExtensions = List("sxm"))

    lazy val vnddotsundotxmldotwriter: MediaType =
      MediaType("application", "vnd.sun.xml.writer", compressible = false, binary = true, fileExtensions = List("sxw"))

    lazy val vnddotsundotxmldotwriterdotglobal: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.writer.global",
        compressible = false,
        binary = true,
        fileExtensions = List("sxg")
      )

    lazy val vnddotsundotxmldotwriterdottemplate: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.writer.template",
        compressible = false,
        binary = true,
        fileExtensions = List("stw")
      )

    lazy val vnddotsuperfiledotsuper: MediaType =
      MediaType("application", "vnd.superfile.super", compressible = false, binary = true)

    lazy val vnddotsusCalendar: MediaType =
      MediaType(
        "application",
        "vnd.sus-calendar",
        compressible = false,
        binary = true,
        fileExtensions = List("sus", "susp")
      )

    lazy val vnddotsvd: MediaType =
      MediaType("application", "vnd.svd", compressible = false, binary = true, fileExtensions = List("svd"))

    lazy val vnddotswiftviewIcs: MediaType =
      MediaType("application", "vnd.swiftview-ics", compressible = false, binary = true)

    lazy val vnddotsybyldotmol2: MediaType =
      MediaType("application", "vnd.sybyl.mol2", compressible = false, binary = true)

    lazy val vnddotsycleplusxml: MediaType =
      MediaType("application", "vnd.sycle+xml", compressible = true, binary = true)

    lazy val vnddotsyftplusjson: MediaType =
      MediaType("application", "vnd.syft+json", compressible = true, binary = false)

    lazy val vnddotsymbiandotinstall: MediaType =
      MediaType(
        "application",
        "vnd.symbian.install",
        compressible = false,
        binary = true,
        fileExtensions = List("sis", "sisx")
      )

    lazy val vnddotsyncmlplusxml: MediaType =
      MediaType("application", "vnd.syncml+xml", compressible = true, binary = true, fileExtensions = List("xsm"))

    lazy val vnddotsyncmldotdmpluswbxml: MediaType =
      MediaType("application", "vnd.syncml.dm+wbxml", compressible = false, binary = true, fileExtensions = List("bdm"))

    lazy val vnddotsyncmldotdmplusxml: MediaType =
      MediaType("application", "vnd.syncml.dm+xml", compressible = true, binary = true, fileExtensions = List("xdm"))

    lazy val vnddotsyncmldotdmdotnotification: MediaType =
      MediaType("application", "vnd.syncml.dm.notification", compressible = false, binary = true)

    lazy val vnddotsyncmldotdmddfpluswbxml: MediaType =
      MediaType("application", "vnd.syncml.dmddf+wbxml", compressible = false, binary = true)

    lazy val vnddotsyncmldotdmddfplusxml: MediaType =
      MediaType("application", "vnd.syncml.dmddf+xml", compressible = true, binary = true, fileExtensions = List("ddf"))

    lazy val vnddotsyncmldotdmtndspluswbxml: MediaType =
      MediaType("application", "vnd.syncml.dmtnds+wbxml", compressible = false, binary = true)

    lazy val vnddotsyncmldotdmtndsplusxml: MediaType =
      MediaType("application", "vnd.syncml.dmtnds+xml", compressible = true, binary = true)

    lazy val vnddotsyncmldotdsdotnotification: MediaType =
      MediaType("application", "vnd.syncml.ds.notification", compressible = false, binary = true)

    lazy val vnddottableschemaplusjson: MediaType =
      MediaType("application", "vnd.tableschema+json", compressible = true, binary = false)

    lazy val vnddottaodotintentModuleArchive: MediaType =
      MediaType(
        "application",
        "vnd.tao.intent-module-archive",
        compressible = false,
        binary = true,
        fileExtensions = List("tao")
      )

    lazy val vnddottcpdumpdotpcap: MediaType =
      MediaType(
        "application",
        "vnd.tcpdump.pcap",
        compressible = false,
        binary = true,
        fileExtensions = List("pcap", "cap", "dmp")
      )

    lazy val vnddotthinkCelldotppttcplusjson: MediaType =
      MediaType("application", "vnd.think-cell.ppttc+json", compressible = true, binary = false)

    lazy val vnddottmddotmediaflexdotapiplusxml: MediaType =
      MediaType("application", "vnd.tmd.mediaflex.api+xml", compressible = true, binary = true)

    lazy val vnddottml: MediaType =
      MediaType("application", "vnd.tml", compressible = false, binary = true)

    lazy val vnddottmobileLivetv: MediaType =
      MediaType("application", "vnd.tmobile-livetv", compressible = false, binary = true, fileExtensions = List("tmo"))

    lazy val vnddottridotonesource: MediaType =
      MediaType("application", "vnd.tri.onesource", compressible = false, binary = true)

    lazy val vnddottriddottpt: MediaType =
      MediaType("application", "vnd.trid.tpt", compressible = false, binary = true, fileExtensions = List("tpt"))

    lazy val vnddottriscapedotmxs: MediaType =
      MediaType("application", "vnd.triscape.mxs", compressible = false, binary = true, fileExtensions = List("mxs"))

    lazy val vnddottrueapp: MediaType =
      MediaType("application", "vnd.trueapp", compressible = false, binary = true, fileExtensions = List("tra"))

    lazy val vnddottruedoc: MediaType =
      MediaType("application", "vnd.truedoc", compressible = false, binary = true)

    lazy val vnddotubisoftdotwebplayer: MediaType =
      MediaType("application", "vnd.ubisoft.webplayer", compressible = false, binary = true)

    lazy val vnddotufdl: MediaType =
      MediaType("application", "vnd.ufdl", compressible = false, binary = true, fileExtensions = List("ufd", "ufdl"))

    lazy val vnddotuicdotdosipasdotv1: MediaType =
      MediaType("application", "vnd.uic.dosipas.v1", compressible = false, binary = true)

    lazy val vnddotuicdotdosipasdotv2: MediaType =
      MediaType("application", "vnd.uic.dosipas.v2", compressible = false, binary = true)

    lazy val vnddotuicdotosdmplusjson: MediaType =
      MediaType("application", "vnd.uic.osdm+json", compressible = true, binary = false)

    lazy val vnddotuicdottlbFcb: MediaType =
      MediaType("application", "vnd.uic.tlb-fcb", compressible = false, binary = true)

    lazy val vnddotuiqdottheme: MediaType =
      MediaType("application", "vnd.uiq.theme", compressible = false, binary = true, fileExtensions = List("utz"))

    lazy val vnddotumajin: MediaType =
      MediaType("application", "vnd.umajin", compressible = false, binary = true, fileExtensions = List("umj"))

    lazy val vnddotunity: MediaType =
      MediaType("application", "vnd.unity", compressible = false, binary = true, fileExtensions = List("unityweb"))

    lazy val vnddotuomlplusxml: MediaType =
      MediaType("application", "vnd.uoml+xml", compressible = true, binary = true, fileExtensions = List("uoml", "uo"))

    lazy val vnddotuplanetdotalert: MediaType =
      MediaType("application", "vnd.uplanet.alert", compressible = false, binary = true)

    lazy val vnddotuplanetdotalertWbxml: MediaType =
      MediaType("application", "vnd.uplanet.alert-wbxml", compressible = false, binary = true)

    lazy val vnddotuplanetdotbearerChoice: MediaType =
      MediaType("application", "vnd.uplanet.bearer-choice", compressible = false, binary = true)

    lazy val vnddotuplanetdotbearerChoiceWbxml: MediaType =
      MediaType("application", "vnd.uplanet.bearer-choice-wbxml", compressible = false, binary = true)

    lazy val vnddotuplanetdotcacheop: MediaType =
      MediaType("application", "vnd.uplanet.cacheop", compressible = false, binary = true)

    lazy val vnddotuplanetdotcacheopWbxml: MediaType =
      MediaType("application", "vnd.uplanet.cacheop-wbxml", compressible = false, binary = true)

    lazy val vnddotuplanetdotchannel: MediaType =
      MediaType("application", "vnd.uplanet.channel", compressible = false, binary = true)

    lazy val vnddotuplanetdotchannelWbxml: MediaType =
      MediaType("application", "vnd.uplanet.channel-wbxml", compressible = false, binary = true)

    lazy val vnddotuplanetdotlist: MediaType =
      MediaType("application", "vnd.uplanet.list", compressible = false, binary = true)

    lazy val vnddotuplanetdotlistWbxml: MediaType =
      MediaType("application", "vnd.uplanet.list-wbxml", compressible = false, binary = true)

    lazy val vnddotuplanetdotlistcmd: MediaType =
      MediaType("application", "vnd.uplanet.listcmd", compressible = false, binary = true)

    lazy val vnddotuplanetdotlistcmdWbxml: MediaType =
      MediaType("application", "vnd.uplanet.listcmd-wbxml", compressible = false, binary = true)

    lazy val vnddotuplanetdotsignal: MediaType =
      MediaType("application", "vnd.uplanet.signal", compressible = false, binary = true)

    lazy val vnddoturiMap: MediaType =
      MediaType("application", "vnd.uri-map", compressible = false, binary = true)

    lazy val vnddotvalvedotsourcedotmaterial: MediaType =
      MediaType("application", "vnd.valve.source.material", compressible = false, binary = true)

    lazy val vnddotvcx: MediaType =
      MediaType("application", "vnd.vcx", compressible = false, binary = true, fileExtensions = List("vcx"))

    lazy val vnddotvdStudy: MediaType =
      MediaType("application", "vnd.vd-study", compressible = false, binary = true)

    lazy val vnddotvectorworks: MediaType =
      MediaType("application", "vnd.vectorworks", compressible = false, binary = true)

    lazy val vnddotvelplusjson: MediaType =
      MediaType("application", "vnd.vel+json", compressible = true, binary = false)

    lazy val vnddotveraisondottsmReportpluscbor: MediaType =
      MediaType("application", "vnd.veraison.tsm-report+cbor", compressible = false, binary = true)

    lazy val vnddotveraisondottsmReportplusjson: MediaType =
      MediaType("application", "vnd.veraison.tsm-report+json", compressible = true, binary = false)

    lazy val vnddotverifierAttestationplusjwt: MediaType =
      MediaType("application", "vnd.verifier-attestation+jwt", compressible = false, binary = true)

    lazy val vnddotverimatrixdotvcas: MediaType =
      MediaType("application", "vnd.verimatrix.vcas", compressible = false, binary = true)

    lazy val vnddotveritonedotaionplusjson: MediaType =
      MediaType("application", "vnd.veritone.aion+json", compressible = true, binary = false)

    lazy val vnddotveryantdotthin: MediaType =
      MediaType("application", "vnd.veryant.thin", compressible = false, binary = true)

    lazy val vnddotvesdotencrypted: MediaType =
      MediaType("application", "vnd.ves.encrypted", compressible = false, binary = true)

    lazy val vnddotvidsoftdotvidconference: MediaType =
      MediaType("application", "vnd.vidsoft.vidconference", compressible = false, binary = true)

    lazy val vnddotvisio: MediaType =
      MediaType(
        "application",
        "vnd.visio",
        compressible = false,
        binary = true,
        fileExtensions = List("vsd", "vst", "vss", "vsw", "vsdx", "vtx")
      )

    lazy val vnddotvisionary: MediaType =
      MediaType("application", "vnd.visionary", compressible = false, binary = true, fileExtensions = List("vis"))

    lazy val vnddotvividencedotscriptfile: MediaType =
      MediaType("application", "vnd.vividence.scriptfile", compressible = false, binary = true)

    lazy val vnddotvocalshaperdotvsp4: MediaType =
      MediaType("application", "vnd.vocalshaper.vsp4", compressible = false, binary = true)

    lazy val vnddotvsf: MediaType =
      MediaType("application", "vnd.vsf", compressible = false, binary = true, fileExtensions = List("vsf"))

    lazy val vnddotvuq: MediaType =
      MediaType("application", "vnd.vuq", compressible = false, binary = true)

    lazy val vnddotwantverse: MediaType =
      MediaType("application", "vnd.wantverse", compressible = false, binary = true)

    lazy val vnddotwapdotsic: MediaType =
      MediaType("application", "vnd.wap.sic", compressible = false, binary = true)

    lazy val vnddotwapdotslc: MediaType =
      MediaType("application", "vnd.wap.slc", compressible = false, binary = true)

    lazy val vnddotwapdotwbxml: MediaType =
      MediaType("application", "vnd.wap.wbxml", compressible = false, binary = true, fileExtensions = List("wbxml"))

    lazy val vnddotwapdotwmlc: MediaType =
      MediaType("application", "vnd.wap.wmlc", compressible = false, binary = true, fileExtensions = List("wmlc"))

    lazy val vnddotwapdotwmlscriptc: MediaType =
      MediaType(
        "application",
        "vnd.wap.wmlscriptc",
        compressible = false,
        binary = true,
        fileExtensions = List("wmlsc")
      )

    lazy val vnddotwasmflowdotwafl: MediaType =
      MediaType("application", "vnd.wasmflow.wafl", compressible = false, binary = true)

    lazy val vnddotwebturbo: MediaType =
      MediaType("application", "vnd.webturbo", compressible = false, binary = true, fileExtensions = List("wtb"))

    lazy val vnddotwfadotdpp: MediaType =
      MediaType("application", "vnd.wfa.dpp", compressible = false, binary = true)

    lazy val vnddotwfadotp2p: MediaType =
      MediaType("application", "vnd.wfa.p2p", compressible = false, binary = true)

    lazy val vnddotwfadotwsc: MediaType =
      MediaType("application", "vnd.wfa.wsc", compressible = false, binary = true)

    lazy val vnddotwindowsdotdevicepairing: MediaType =
      MediaType("application", "vnd.windows.devicepairing", compressible = false, binary = true)

    lazy val vnddotwmap: MediaType =
      MediaType("application", "vnd.wmap", compressible = false, binary = true)

    lazy val vnddotwmc: MediaType =
      MediaType("application", "vnd.wmc", compressible = false, binary = true)

    lazy val vnddotwmfdotbootstrap: MediaType =
      MediaType("application", "vnd.wmf.bootstrap", compressible = false, binary = true)

    lazy val vnddotwolframdotmathematica: MediaType =
      MediaType("application", "vnd.wolfram.mathematica", compressible = false, binary = true)

    lazy val vnddotwolframdotmathematicadotpackage: MediaType =
      MediaType("application", "vnd.wolfram.mathematica.package", compressible = false, binary = true)

    lazy val vnddotwolframdotplayer: MediaType =
      MediaType("application", "vnd.wolfram.player", compressible = false, binary = true, fileExtensions = List("nbp"))

    lazy val vnddotwordlift: MediaType =
      MediaType("application", "vnd.wordlift", compressible = false, binary = true)

    lazy val vnddotwordperfect: MediaType =
      MediaType("application", "vnd.wordperfect", compressible = false, binary = true, fileExtensions = List("wpd"))

    lazy val vnddotwqd: MediaType =
      MediaType("application", "vnd.wqd", compressible = false, binary = true, fileExtensions = List("wqd"))

    lazy val vnddotwrqHp3000Labelled: MediaType =
      MediaType("application", "vnd.wrq-hp3000-labelled", compressible = false, binary = true)

    lazy val vnddotwtdotstf: MediaType =
      MediaType("application", "vnd.wt.stf", compressible = false, binary = true, fileExtensions = List("stf"))

    lazy val vnddotwvdotcsppluswbxml: MediaType =
      MediaType("application", "vnd.wv.csp+wbxml", compressible = false, binary = true)

    lazy val vnddotwvdotcspplusxml: MediaType =
      MediaType("application", "vnd.wv.csp+xml", compressible = true, binary = true)

    lazy val vnddotwvdotsspplusxml: MediaType =
      MediaType("application", "vnd.wv.ssp+xml", compressible = true, binary = true)

    lazy val vnddotxacmlplusjson: MediaType =
      MediaType("application", "vnd.xacml+json", compressible = true, binary = false)

    lazy val vnddotxara: MediaType =
      MediaType("application", "vnd.xara", compressible = false, binary = true, fileExtensions = List("xar"))

    lazy val vnddotxarindotcpj: MediaType =
      MediaType("application", "vnd.xarin.cpj", compressible = false, binary = true)

    lazy val vnddotxcdn: MediaType =
      MediaType("application", "vnd.xcdn", compressible = false, binary = true)

    lazy val vnddotxecretsEncrypted: MediaType =
      MediaType("application", "vnd.xecrets-encrypted", compressible = false, binary = true)

    lazy val vnddotxfdl: MediaType =
      MediaType("application", "vnd.xfdl", compressible = false, binary = true, fileExtensions = List("xfdl"))

    lazy val vnddotxfdldotwebform: MediaType =
      MediaType("application", "vnd.xfdl.webform", compressible = false, binary = true)

    lazy val vnddotxmiplusxml: MediaType =
      MediaType("application", "vnd.xmi+xml", compressible = true, binary = true)

    lazy val vnddotxmpiedotcpkg: MediaType =
      MediaType("application", "vnd.xmpie.cpkg", compressible = false, binary = true)

    lazy val vnddotxmpiedotdpkg: MediaType =
      MediaType("application", "vnd.xmpie.dpkg", compressible = false, binary = true)

    lazy val vnddotxmpiedotplan: MediaType =
      MediaType("application", "vnd.xmpie.plan", compressible = false, binary = true)

    lazy val vnddotxmpiedotppkg: MediaType =
      MediaType("application", "vnd.xmpie.ppkg", compressible = false, binary = true)

    lazy val vnddotxmpiedotxlim: MediaType =
      MediaType("application", "vnd.xmpie.xlim", compressible = false, binary = true)

    lazy val vnddotyamahadothvDic: MediaType =
      MediaType("application", "vnd.yamaha.hv-dic", compressible = false, binary = true, fileExtensions = List("hvd"))

    lazy val vnddotyamahadothvScript: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.hv-script",
        compressible = false,
        binary = true,
        fileExtensions = List("hvs")
      )

    lazy val vnddotyamahadothvVoice: MediaType =
      MediaType("application", "vnd.yamaha.hv-voice", compressible = false, binary = true, fileExtensions = List("hvp"))

    lazy val vnddotyamahadotopenscoreformat: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.openscoreformat",
        compressible = false,
        binary = true,
        fileExtensions = List("osf")
      )

    lazy val vnddotyamahadotopenscoreformatdotosfpvgplusxml: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.openscoreformat.osfpvg+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("osfpvg")
      )

    lazy val vnddotyamahadotremoteSetup: MediaType =
      MediaType("application", "vnd.yamaha.remote-setup", compressible = false, binary = true)

    lazy val vnddotyamahadotsmafAudio: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.smaf-audio",
        compressible = false,
        binary = true,
        fileExtensions = List("saf")
      )

    lazy val vnddotyamahadotsmafPhrase: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.smaf-phrase",
        compressible = false,
        binary = true,
        fileExtensions = List("spf")
      )

    lazy val vnddotyamahadotthroughNgn: MediaType =
      MediaType("application", "vnd.yamaha.through-ngn", compressible = false, binary = true)

    lazy val vnddotyamahadottunnelUdpencap: MediaType =
      MediaType("application", "vnd.yamaha.tunnel-udpencap", compressible = false, binary = true)

    lazy val vnddotyaoweme: MediaType =
      MediaType("application", "vnd.yaoweme", compressible = false, binary = true)

    lazy val vnddotyellowriverCustomMenu: MediaType =
      MediaType(
        "application",
        "vnd.yellowriver-custom-menu",
        compressible = false,
        binary = true,
        fileExtensions = List("cmp")
      )

    lazy val vnddotzohoPresentationdotshow: MediaType =
      MediaType("application", "vnd.zoho-presentation.show", compressible = false, binary = true)

    lazy val vnddotzul: MediaType =
      MediaType("application", "vnd.zul", compressible = false, binary = true, fileExtensions = List("zir", "zirz"))

    lazy val vnddotzzazzdotdeckplusxml: MediaType =
      MediaType("application", "vnd.zzazz.deck+xml", compressible = true, binary = true, fileExtensions = List("zaz"))

    lazy val voicexmlplusxml: MediaType =
      MediaType("application", "voicexml+xml", compressible = true, binary = true, fileExtensions = List("vxml"))

    lazy val voucherCmsplusjson: MediaType =
      MediaType("application", "voucher-cms+json", compressible = true, binary = false)

    lazy val voucherJwsplusjson: MediaType =
      MediaType("application", "voucher-jws+json", compressible = true, binary = false)

    lazy val vp: MediaType =
      MediaType("application", "vp", compressible = false, binary = true)

    lazy val vppluscose: MediaType =
      MediaType("application", "vp+cose", compressible = false, binary = true)

    lazy val vpplusjwt: MediaType =
      MediaType("application", "vp+jwt", compressible = false, binary = true)

    lazy val vpplussdJwt: MediaType =
      MediaType("application", "vp+sd-jwt", compressible = false, binary = true)

    lazy val vqRtcpxr: MediaType =
      MediaType("application", "vq-rtcpxr", compressible = false, binary = true)

    lazy val wasm: MediaType =
      MediaType("application", "wasm", compressible = true, binary = true, fileExtensions = List("wasm"))

    lazy val watcherinfoplusxml: MediaType =
      MediaType("application", "watcherinfo+xml", compressible = true, binary = true, fileExtensions = List("wif"))

    lazy val webpushOptionsplusjson: MediaType =
      MediaType("application", "webpush-options+json", compressible = true, binary = false)

    lazy val whoisppQuery: MediaType =
      MediaType("application", "whoispp-query", compressible = false, binary = true)

    lazy val whoisppResponse: MediaType =
      MediaType("application", "whoispp-response", compressible = false, binary = true)

    lazy val widget: MediaType =
      MediaType("application", "widget", compressible = false, binary = true, fileExtensions = List("wgt"))

    lazy val winhlp: MediaType =
      MediaType("application", "winhlp", compressible = false, binary = true, fileExtensions = List("hlp"))

    lazy val wita: MediaType =
      MediaType("application", "wita", compressible = false, binary = true)

    lazy val wordperfect5dot1: MediaType =
      MediaType("application", "wordperfect5.1", compressible = false, binary = true)

    lazy val wsdlplusxml: MediaType =
      MediaType("application", "wsdl+xml", compressible = true, binary = true, fileExtensions = List("wsdl"))

    lazy val wspolicyplusxml: MediaType =
      MediaType("application", "wspolicy+xml", compressible = true, binary = true, fileExtensions = List("wspolicy"))

    lazy val x7zCompressed: MediaType =
      MediaType("application", "x-7z-compressed", compressible = false, binary = true, fileExtensions = List("7z"))

    lazy val xAbiword: MediaType =
      MediaType("application", "x-abiword", compressible = false, binary = true, fileExtensions = List("abw"))

    lazy val xAceCompressed: MediaType =
      MediaType("application", "x-ace-compressed", compressible = false, binary = true, fileExtensions = List("ace"))

    lazy val xAmf: MediaType =
      MediaType("application", "x-amf", compressible = false, binary = true)

    lazy val xAppleDiskimage: MediaType =
      MediaType("application", "x-apple-diskimage", compressible = false, binary = true, fileExtensions = List("dmg"))

    lazy val xArj: MediaType =
      MediaType("application", "x-arj", compressible = false, binary = true, fileExtensions = List("arj"))

    lazy val xAuthorwareBin: MediaType =
      MediaType(
        "application",
        "x-authorware-bin",
        compressible = false,
        binary = true,
        fileExtensions = List("aab", "x32", "u32", "vox")
      )

    lazy val xAuthorwareMap: MediaType =
      MediaType("application", "x-authorware-map", compressible = false, binary = true, fileExtensions = List("aam"))

    lazy val xAuthorwareSeg: MediaType =
      MediaType("application", "x-authorware-seg", compressible = false, binary = true, fileExtensions = List("aas"))

    lazy val xBcpio: MediaType =
      MediaType("application", "x-bcpio", compressible = false, binary = true, fileExtensions = List("bcpio"))

    lazy val xBdoc: MediaType =
      MediaType("application", "x-bdoc", compressible = false, binary = true, fileExtensions = List("bdoc"))

    lazy val xBittorrent: MediaType =
      MediaType("application", "x-bittorrent", compressible = false, binary = true, fileExtensions = List("torrent"))

    lazy val xBlender: MediaType =
      MediaType("application", "x-blender", compressible = false, binary = true, fileExtensions = List("blend"))

    lazy val xBlorb: MediaType =
      MediaType("application", "x-blorb", compressible = false, binary = true, fileExtensions = List("blb", "blorb"))

    lazy val xBzip: MediaType =
      MediaType("application", "x-bzip", compressible = false, binary = true, fileExtensions = List("bz"))

    lazy val xBzip2: MediaType =
      MediaType("application", "x-bzip2", compressible = false, binary = true, fileExtensions = List("bz2", "boz"))

    lazy val xCbr: MediaType =
      MediaType(
        "application",
        "x-cbr",
        compressible = false,
        binary = true,
        fileExtensions = List("cbr", "cba", "cbt", "cbz", "cb7")
      )

    lazy val xCdlink: MediaType =
      MediaType("application", "x-cdlink", compressible = false, binary = true, fileExtensions = List("vcd"))

    lazy val xCfsCompressed: MediaType =
      MediaType("application", "x-cfs-compressed", compressible = false, binary = true, fileExtensions = List("cfs"))

    lazy val xChat: MediaType =
      MediaType("application", "x-chat", compressible = false, binary = true, fileExtensions = List("chat"))

    lazy val xChessPgn: MediaType =
      MediaType("application", "x-chess-pgn", compressible = false, binary = true, fileExtensions = List("pgn"))

    lazy val xChromeExtension: MediaType =
      MediaType("application", "x-chrome-extension", compressible = false, binary = true, fileExtensions = List("crx"))

    lazy val xCocoa: MediaType =
      MediaType("application", "x-cocoa", compressible = false, binary = true, fileExtensions = List("cco"))

    lazy val xCompress: MediaType =
      MediaType("application", "x-compress", compressible = false, binary = true)

    lazy val xCompressed: MediaType =
      MediaType("application", "x-compressed", compressible = false, binary = true, fileExtensions = List("rar"))

    lazy val xConference: MediaType =
      MediaType("application", "x-conference", compressible = false, binary = true, fileExtensions = List("nsc"))

    lazy val xCpio: MediaType =
      MediaType("application", "x-cpio", compressible = false, binary = true, fileExtensions = List("cpio"))

    lazy val xCsh: MediaType =
      MediaType("application", "x-csh", compressible = false, binary = true, fileExtensions = List("csh"))

    lazy val xDeb: MediaType =
      MediaType("application", "x-deb", compressible = false, binary = true)

    lazy val xDebianPackage: MediaType =
      MediaType(
        "application",
        "x-debian-package",
        compressible = false,
        binary = true,
        fileExtensions = List("deb", "udeb")
      )

    lazy val xDgcCompressed: MediaType =
      MediaType("application", "x-dgc-compressed", compressible = false, binary = true, fileExtensions = List("dgc"))

    lazy val xDirector: MediaType =
      MediaType(
        "application",
        "x-director",
        compressible = false,
        binary = true,
        fileExtensions = List("dir", "dcr", "dxr", "cst", "cct", "cxt", "w3d", "fgd", "swa")
      )

    lazy val xDoom: MediaType =
      MediaType("application", "x-doom", compressible = false, binary = true, fileExtensions = List("wad"))

    lazy val xDtbncxplusxml: MediaType =
      MediaType("application", "x-dtbncx+xml", compressible = true, binary = true, fileExtensions = List("ncx"))

    lazy val xDtbookplusxml: MediaType =
      MediaType("application", "x-dtbook+xml", compressible = true, binary = true, fileExtensions = List("dtb"))

    lazy val xDtbresourceplusxml: MediaType =
      MediaType("application", "x-dtbresource+xml", compressible = true, binary = true, fileExtensions = List("res"))

    lazy val xDvi: MediaType =
      MediaType("application", "x-dvi", compressible = false, binary = true, fileExtensions = List("dvi"))

    lazy val xEnvoy: MediaType =
      MediaType("application", "x-envoy", compressible = false, binary = true, fileExtensions = List("evy"))

    lazy val xEva: MediaType =
      MediaType("application", "x-eva", compressible = false, binary = true, fileExtensions = List("eva"))

    lazy val xFontBdf: MediaType =
      MediaType("application", "x-font-bdf", compressible = false, binary = true, fileExtensions = List("bdf"))

    lazy val xFontDos: MediaType =
      MediaType("application", "x-font-dos", compressible = false, binary = true)

    lazy val xFontFramemaker: MediaType =
      MediaType("application", "x-font-framemaker", compressible = false, binary = true)

    lazy val xFontGhostscript: MediaType =
      MediaType("application", "x-font-ghostscript", compressible = false, binary = true, fileExtensions = List("gsf"))

    lazy val xFontLibgrx: MediaType =
      MediaType("application", "x-font-libgrx", compressible = false, binary = true)

    lazy val xFontLinuxPsf: MediaType =
      MediaType("application", "x-font-linux-psf", compressible = false, binary = true, fileExtensions = List("psf"))

    lazy val xFontPcf: MediaType =
      MediaType("application", "x-font-pcf", compressible = false, binary = true, fileExtensions = List("pcf"))

    lazy val xFontSnf: MediaType =
      MediaType("application", "x-font-snf", compressible = false, binary = true, fileExtensions = List("snf"))

    lazy val xFontSpeedo: MediaType =
      MediaType("application", "x-font-speedo", compressible = false, binary = true)

    lazy val xFontSunosNews: MediaType =
      MediaType("application", "x-font-sunos-news", compressible = false, binary = true)

    lazy val xFontType1: MediaType =
      MediaType(
        "application",
        "x-font-type1",
        compressible = false,
        binary = true,
        fileExtensions = List("pfa", "pfb", "pfm", "afm")
      )

    lazy val xFontVfont: MediaType =
      MediaType("application", "x-font-vfont", compressible = false, binary = true)

    lazy val xFreearc: MediaType =
      MediaType("application", "x-freearc", compressible = false, binary = true, fileExtensions = List("arc"))

    lazy val xFuturesplash: MediaType =
      MediaType("application", "x-futuresplash", compressible = false, binary = true, fileExtensions = List("spl"))

    lazy val xGcaCompressed: MediaType =
      MediaType("application", "x-gca-compressed", compressible = false, binary = true, fileExtensions = List("gca"))

    lazy val xGlulx: MediaType =
      MediaType("application", "x-glulx", compressible = false, binary = true, fileExtensions = List("ulx"))

    lazy val xGnumeric: MediaType =
      MediaType("application", "x-gnumeric", compressible = false, binary = true, fileExtensions = List("gnumeric"))

    lazy val xGrampsXml: MediaType =
      MediaType("application", "x-gramps-xml", compressible = false, binary = true, fileExtensions = List("gramps"))

    lazy val xGtar: MediaType =
      MediaType("application", "x-gtar", compressible = false, binary = true, fileExtensions = List("gtar"))

    lazy val xGzip: MediaType =
      MediaType("application", "x-gzip", compressible = false, binary = true)

    lazy val xHdf: MediaType =
      MediaType("application", "x-hdf", compressible = false, binary = true, fileExtensions = List("hdf"))

    lazy val xHttpdPhp: MediaType =
      MediaType("application", "x-httpd-php", compressible = true, binary = true, fileExtensions = List("php"))

    lazy val xInstallInstructions: MediaType =
      MediaType(
        "application",
        "x-install-instructions",
        compressible = false,
        binary = true,
        fileExtensions = List("install")
      )

    lazy val xIpynbplusjson: MediaType =
      MediaType("application", "x-ipynb+json", compressible = true, binary = false, fileExtensions = List("ipynb"))

    lazy val xIso9660Image: MediaType =
      MediaType("application", "x-iso9660-image", compressible = false, binary = true, fileExtensions = List("iso"))

    lazy val xIworkKeynoteSffkey: MediaType =
      MediaType(
        "application",
        "x-iwork-keynote-sffkey",
        compressible = false,
        binary = true,
        fileExtensions = List("key")
      )

    lazy val xIworkNumbersSffnumbers: MediaType =
      MediaType(
        "application",
        "x-iwork-numbers-sffnumbers",
        compressible = false,
        binary = true,
        fileExtensions = List("numbers")
      )

    lazy val xIworkPagesSffpages: MediaType =
      MediaType(
        "application",
        "x-iwork-pages-sffpages",
        compressible = false,
        binary = true,
        fileExtensions = List("pages")
      )

    lazy val xJavaArchiveDiff: MediaType =
      MediaType(
        "application",
        "x-java-archive-diff",
        compressible = false,
        binary = true,
        fileExtensions = List("jardiff")
      )

    lazy val xJavaJnlpFile: MediaType =
      MediaType("application", "x-java-jnlp-file", compressible = false, binary = true, fileExtensions = List("jnlp"))

    lazy val xJavascript: MediaType =
      MediaType("application", "x-javascript", compressible = true, binary = false)

    lazy val xKeepass2: MediaType =
      MediaType("application", "x-keepass2", compressible = false, binary = true, fileExtensions = List("kdbx"))

    lazy val xLatex: MediaType =
      MediaType("application", "x-latex", compressible = false, binary = true, fileExtensions = List("latex"))

    lazy val xLuaBytecode: MediaType =
      MediaType("application", "x-lua-bytecode", compressible = false, binary = true, fileExtensions = List("luac"))

    lazy val xLzhCompressed: MediaType =
      MediaType(
        "application",
        "x-lzh-compressed",
        compressible = false,
        binary = true,
        fileExtensions = List("lzh", "lha")
      )

    lazy val xMakeself: MediaType =
      MediaType("application", "x-makeself", compressible = false, binary = true, fileExtensions = List("run"))

    lazy val xMie: MediaType =
      MediaType("application", "x-mie", compressible = false, binary = true, fileExtensions = List("mie"))

    lazy val xMobipocketEbook: MediaType =
      MediaType(
        "application",
        "x-mobipocket-ebook",
        compressible = false,
        binary = true,
        fileExtensions = List("prc", "mobi")
      )

    lazy val xMpegurl: MediaType =
      MediaType("application", "x-mpegurl", compressible = false, binary = true)

    lazy val xMsApplication: MediaType =
      MediaType(
        "application",
        "x-ms-application",
        compressible = false,
        binary = true,
        fileExtensions = List("application")
      )

    lazy val xMsShortcut: MediaType =
      MediaType("application", "x-ms-shortcut", compressible = false, binary = true, fileExtensions = List("lnk"))

    lazy val xMsWmd: MediaType =
      MediaType("application", "x-ms-wmd", compressible = false, binary = true, fileExtensions = List("wmd"))

    lazy val xMsWmz: MediaType =
      MediaType("application", "x-ms-wmz", compressible = false, binary = true, fileExtensions = List("wmz"))

    lazy val xMsXbap: MediaType =
      MediaType("application", "x-ms-xbap", compressible = false, binary = true, fileExtensions = List("xbap"))

    lazy val xMsaccess: MediaType =
      MediaType("application", "x-msaccess", compressible = false, binary = true, fileExtensions = List("mdb"))

    lazy val xMsbinder: MediaType =
      MediaType("application", "x-msbinder", compressible = false, binary = true, fileExtensions = List("obd"))

    lazy val xMscardfile: MediaType =
      MediaType("application", "x-mscardfile", compressible = false, binary = true, fileExtensions = List("crd"))

    lazy val xMsclip: MediaType =
      MediaType("application", "x-msclip", compressible = false, binary = true, fileExtensions = List("clp"))

    lazy val xMsdosProgram: MediaType =
      MediaType("application", "x-msdos-program", compressible = false, binary = true, fileExtensions = List("exe"))

    lazy val xMsdownload: MediaType =
      MediaType(
        "application",
        "x-msdownload",
        compressible = false,
        binary = true,
        fileExtensions = List("exe", "dll", "com", "bat", "msi")
      )

    lazy val xMsmediaview: MediaType =
      MediaType(
        "application",
        "x-msmediaview",
        compressible = false,
        binary = true,
        fileExtensions = List("mvb", "m13", "m14")
      )

    lazy val xMsmetafile: MediaType =
      MediaType(
        "application",
        "x-msmetafile",
        compressible = false,
        binary = true,
        fileExtensions = List("wmf", "wmz", "emf", "emz")
      )

    lazy val xMsmoney: MediaType =
      MediaType("application", "x-msmoney", compressible = false, binary = true, fileExtensions = List("mny"))

    lazy val xMspublisher: MediaType =
      MediaType("application", "x-mspublisher", compressible = false, binary = true, fileExtensions = List("pub"))

    lazy val xMsschedule: MediaType =
      MediaType("application", "x-msschedule", compressible = false, binary = true, fileExtensions = List("scd"))

    lazy val xMsterminal: MediaType =
      MediaType("application", "x-msterminal", compressible = false, binary = true, fileExtensions = List("trm"))

    lazy val xMswrite: MediaType =
      MediaType("application", "x-mswrite", compressible = false, binary = true, fileExtensions = List("wri"))

    lazy val xNetcdf: MediaType =
      MediaType("application", "x-netcdf", compressible = false, binary = true, fileExtensions = List("nc", "cdf"))

    lazy val xNsProxyAutoconfig: MediaType =
      MediaType(
        "application",
        "x-ns-proxy-autoconfig",
        compressible = true,
        binary = true,
        fileExtensions = List("pac")
      )

    lazy val xNzb: MediaType =
      MediaType("application", "x-nzb", compressible = false, binary = true, fileExtensions = List("nzb"))

    lazy val xPerl: MediaType =
      MediaType("application", "x-perl", compressible = false, binary = true, fileExtensions = List("pl", "pm"))

    lazy val xPilot: MediaType =
      MediaType("application", "x-pilot", compressible = false, binary = true, fileExtensions = List("prc", "pdb"))

    lazy val xPkcs12: MediaType =
      MediaType("application", "x-pkcs12", compressible = false, binary = true, fileExtensions = List("p12", "pfx"))

    lazy val xPkcs7Certificates: MediaType =
      MediaType(
        "application",
        "x-pkcs7-certificates",
        compressible = false,
        binary = true,
        fileExtensions = List("p7b", "spc")
      )

    lazy val xPkcs7Certreqresp: MediaType =
      MediaType("application", "x-pkcs7-certreqresp", compressible = false, binary = true, fileExtensions = List("p7r"))

    lazy val xPkiMessage: MediaType =
      MediaType("application", "x-pki-message", compressible = false, binary = true)

    lazy val xRarCompressed: MediaType =
      MediaType("application", "x-rar-compressed", compressible = false, binary = true, fileExtensions = List("rar"))

    lazy val xRedhatPackageManager: MediaType =
      MediaType(
        "application",
        "x-redhat-package-manager",
        compressible = false,
        binary = true,
        fileExtensions = List("rpm")
      )

    lazy val xResearchInfoSystems: MediaType =
      MediaType(
        "application",
        "x-research-info-systems",
        compressible = false,
        binary = true,
        fileExtensions = List("ris")
      )

    lazy val xSea: MediaType =
      MediaType("application", "x-sea", compressible = false, binary = true, fileExtensions = List("sea"))

    lazy val xSh: MediaType =
      MediaType("application", "x-sh", compressible = true, binary = true, fileExtensions = List("sh"))

    lazy val xShar: MediaType =
      MediaType("application", "x-shar", compressible = false, binary = true, fileExtensions = List("shar"))

    lazy val xShockwaveFlash: MediaType =
      MediaType("application", "x-shockwave-flash", compressible = false, binary = true, fileExtensions = List("swf"))

    lazy val xSilverlightApp: MediaType =
      MediaType("application", "x-silverlight-app", compressible = false, binary = true, fileExtensions = List("xap"))

    lazy val xSql: MediaType =
      MediaType("application", "x-sql", compressible = false, binary = true, fileExtensions = List("sql"))

    lazy val xStuffit: MediaType =
      MediaType("application", "x-stuffit", compressible = false, binary = true, fileExtensions = List("sit"))

    lazy val xStuffitx: MediaType =
      MediaType("application", "x-stuffitx", compressible = false, binary = true, fileExtensions = List("sitx"))

    lazy val xSubrip: MediaType =
      MediaType("application", "x-subrip", compressible = false, binary = true, fileExtensions = List("srt"))

    lazy val xSv4cpio: MediaType =
      MediaType("application", "x-sv4cpio", compressible = false, binary = true, fileExtensions = List("sv4cpio"))

    lazy val xSv4crc: MediaType =
      MediaType("application", "x-sv4crc", compressible = false, binary = true, fileExtensions = List("sv4crc"))

    lazy val xT3vmImage: MediaType =
      MediaType("application", "x-t3vm-image", compressible = false, binary = true, fileExtensions = List("t3"))

    lazy val xTads: MediaType =
      MediaType("application", "x-tads", compressible = false, binary = true, fileExtensions = List("gam"))

    lazy val xTar: MediaType =
      MediaType("application", "x-tar", compressible = true, binary = true, fileExtensions = List("tar"))

    lazy val xTcl: MediaType =
      MediaType("application", "x-tcl", compressible = false, binary = true, fileExtensions = List("tcl", "tk"))

    lazy val xTex: MediaType =
      MediaType("application", "x-tex", compressible = false, binary = true, fileExtensions = List("tex"))

    lazy val xTexTfm: MediaType =
      MediaType("application", "x-tex-tfm", compressible = false, binary = true, fileExtensions = List("tfm"))

    lazy val xTexinfo: MediaType =
      MediaType(
        "application",
        "x-texinfo",
        compressible = false,
        binary = true,
        fileExtensions = List("texinfo", "texi")
      )

    lazy val xTgif: MediaType =
      MediaType("application", "x-tgif", compressible = false, binary = true, fileExtensions = List("obj"))

    lazy val xUstar: MediaType =
      MediaType("application", "x-ustar", compressible = false, binary = true, fileExtensions = List("ustar"))

    lazy val xVirtualboxHdd: MediaType =
      MediaType("application", "x-virtualbox-hdd", compressible = true, binary = true, fileExtensions = List("hdd"))

    lazy val xVirtualboxOva: MediaType =
      MediaType("application", "x-virtualbox-ova", compressible = true, binary = true, fileExtensions = List("ova"))

    lazy val xVirtualboxOvf: MediaType =
      MediaType("application", "x-virtualbox-ovf", compressible = true, binary = true, fileExtensions = List("ovf"))

    lazy val xVirtualboxVbox: MediaType =
      MediaType("application", "x-virtualbox-vbox", compressible = true, binary = true, fileExtensions = List("vbox"))

    lazy val xVirtualboxVboxExtpack: MediaType =
      MediaType(
        "application",
        "x-virtualbox-vbox-extpack",
        compressible = false,
        binary = true,
        fileExtensions = List("vbox-extpack")
      )

    lazy val xVirtualboxVdi: MediaType =
      MediaType("application", "x-virtualbox-vdi", compressible = true, binary = true, fileExtensions = List("vdi"))

    lazy val xVirtualboxVhd: MediaType =
      MediaType("application", "x-virtualbox-vhd", compressible = true, binary = true, fileExtensions = List("vhd"))

    lazy val xVirtualboxVmdk: MediaType =
      MediaType("application", "x-virtualbox-vmdk", compressible = true, binary = true, fileExtensions = List("vmdk"))

    lazy val xWaisSource: MediaType =
      MediaType("application", "x-wais-source", compressible = false, binary = true, fileExtensions = List("src"))

    lazy val xWebAppManifestplusjson: MediaType =
      MediaType(
        "application",
        "x-web-app-manifest+json",
        compressible = true,
        binary = false,
        fileExtensions = List("webapp")
      )

    lazy val xWwwFormUrlencoded: MediaType =
      MediaType("application", "x-www-form-urlencoded", compressible = true, binary = true)

    lazy val xX509CaCert: MediaType =
      MediaType(
        "application",
        "x-x509-ca-cert",
        compressible = false,
        binary = true,
        fileExtensions = List("der", "crt", "pem")
      )

    lazy val xX509CaRaCert: MediaType =
      MediaType("application", "x-x509-ca-ra-cert", compressible = false, binary = true)

    lazy val xX509NextCaCert: MediaType =
      MediaType("application", "x-x509-next-ca-cert", compressible = false, binary = true)

    lazy val xXfig: MediaType =
      MediaType("application", "x-xfig", compressible = false, binary = true, fileExtensions = List("fig"))

    lazy val xXliffplusxml: MediaType =
      MediaType("application", "x-xliff+xml", compressible = true, binary = true, fileExtensions = List("xlf"))

    lazy val xXpinstall: MediaType =
      MediaType("application", "x-xpinstall", compressible = false, binary = true, fileExtensions = List("xpi"))

    lazy val xXz: MediaType =
      MediaType("application", "x-xz", compressible = false, binary = true, fileExtensions = List("xz"))

    lazy val xZipCompressed: MediaType =
      MediaType("application", "x-zip-compressed", compressible = false, binary = true, fileExtensions = List("zip"))

    lazy val xZmachine: MediaType =
      MediaType(
        "application",
        "x-zmachine",
        compressible = false,
        binary = true,
        fileExtensions = List("z1", "z2", "z3", "z4", "z5", "z6", "z7", "z8")
      )

    lazy val x400Bp: MediaType =
      MediaType("application", "x400-bp", compressible = false, binary = true)

    lazy val xacmlplusxml: MediaType =
      MediaType("application", "xacml+xml", compressible = true, binary = true)

    lazy val xamlplusxml: MediaType =
      MediaType("application", "xaml+xml", compressible = true, binary = true, fileExtensions = List("xaml"))

    lazy val xcapAttplusxml: MediaType =
      MediaType("application", "xcap-att+xml", compressible = true, binary = true, fileExtensions = List("xav"))

    lazy val xcapCapsplusxml: MediaType =
      MediaType("application", "xcap-caps+xml", compressible = true, binary = true, fileExtensions = List("xca"))

    lazy val xcapDiffplusxml: MediaType =
      MediaType("application", "xcap-diff+xml", compressible = true, binary = true, fileExtensions = List("xdf"))

    lazy val xcapElplusxml: MediaType =
      MediaType("application", "xcap-el+xml", compressible = true, binary = true, fileExtensions = List("xel"))

    lazy val xcapErrorplusxml: MediaType =
      MediaType("application", "xcap-error+xml", compressible = true, binary = true)

    lazy val xcapNsplusxml: MediaType =
      MediaType("application", "xcap-ns+xml", compressible = true, binary = true, fileExtensions = List("xns"))

    lazy val xconConferenceInfoplusxml: MediaType =
      MediaType("application", "xcon-conference-info+xml", compressible = true, binary = true)

    lazy val xconConferenceInfoDiffplusxml: MediaType =
      MediaType("application", "xcon-conference-info-diff+xml", compressible = true, binary = true)

    lazy val xencplusxml: MediaType =
      MediaType("application", "xenc+xml", compressible = true, binary = true, fileExtensions = List("xenc"))

    lazy val xfdf: MediaType =
      MediaType("application", "xfdf", compressible = false, binary = true, fileExtensions = List("xfdf"))

    lazy val xhtmlplusxml: MediaType =
      MediaType("application", "xhtml+xml", compressible = true, binary = true, fileExtensions = List("xhtml", "xht"))

    lazy val xhtmlVoiceplusxml: MediaType =
      MediaType("application", "xhtml-voice+xml", compressible = true, binary = true)

    lazy val xliffplusxml: MediaType =
      MediaType("application", "xliff+xml", compressible = true, binary = true, fileExtensions = List("xlf"))

    lazy val xml: MediaType =
      MediaType(
        "application",
        "xml",
        compressible = true,
        binary = false,
        fileExtensions = List("xml", "xsl", "xsd", "rng")
      )

    lazy val xmlDtd: MediaType =
      MediaType("application", "xml-dtd", compressible = true, binary = false, fileExtensions = List("dtd"))

    lazy val xmlExternalParsedEntity: MediaType =
      MediaType("application", "xml-external-parsed-entity", compressible = false, binary = false)

    lazy val xmlPatchplusxml: MediaType =
      MediaType("application", "xml-patch+xml", compressible = true, binary = false)

    lazy val xmppplusxml: MediaType =
      MediaType("application", "xmpp+xml", compressible = true, binary = true)

    lazy val xopplusxml: MediaType =
      MediaType("application", "xop+xml", compressible = true, binary = true, fileExtensions = List("xop"))

    lazy val xprocplusxml: MediaType =
      MediaType("application", "xproc+xml", compressible = true, binary = true, fileExtensions = List("xpl"))

    lazy val xsltplusxml: MediaType =
      MediaType("application", "xslt+xml", compressible = true, binary = true, fileExtensions = List("xsl", "xslt"))

    lazy val xspfplusxml: MediaType =
      MediaType("application", "xspf+xml", compressible = true, binary = true, fileExtensions = List("xspf"))

    lazy val xvplusxml: MediaType =
      MediaType(
        "application",
        "xv+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("mxml", "xhvml", "xvml", "xvm")
      )

    lazy val yaml: MediaType =
      MediaType("application", "yaml", compressible = true, binary = true)

    lazy val yang: MediaType =
      MediaType("application", "yang", compressible = false, binary = true, fileExtensions = List("yang"))

    lazy val yangDatapluscbor: MediaType =
      MediaType("application", "yang-data+cbor", compressible = false, binary = true)

    lazy val yangDataplusjson: MediaType =
      MediaType("application", "yang-data+json", compressible = true, binary = false)

    lazy val yangDataplusxml: MediaType =
      MediaType("application", "yang-data+xml", compressible = true, binary = true)

    lazy val yangPatchplusjson: MediaType =
      MediaType("application", "yang-patch+json", compressible = true, binary = false)

    lazy val yangPatchplusxml: MediaType =
      MediaType("application", "yang-patch+xml", compressible = true, binary = true)

    lazy val yangSidplusjson: MediaType =
      MediaType("application", "yang-sid+json", compressible = true, binary = false)

    lazy val yinplusxml: MediaType =
      MediaType("application", "yin+xml", compressible = true, binary = true, fileExtensions = List("yin"))

    lazy val zip: MediaType =
      MediaType("application", "zip", compressible = false, binary = true, fileExtensions = List("zip"))

    lazy val zipplusdotlottie: MediaType =
      MediaType("application", "zip+dotlottie", compressible = false, binary = true, fileExtensions = List("lottie"))

    lazy val zlib: MediaType =
      MediaType("application", "zlib", compressible = false, binary = true)

    lazy val zstd: MediaType =
      MediaType("application", "zstd", compressible = false, binary = true)

    def all: List[MediaType] = List(
      _1dInterleavedParityfec,
      _3gpdashQoeReportplusxml,
      _3gppImsplusxml,
      _3gppMbsObjectManifestplusjson,
      _3gppMbsUserServiceDescriptionsplusjson,
      _3gppMediaDeliveryMetricsReportplusjson,
      _3gpphalplusjson,
      _3gpphalformsplusjson,
      a2l,
      acepluscbor,
      aceplusjson,
      aceGroupcommpluscbor,
      aceTrlpluscbor,
      activemessage,
      activityplusjson,
      aifpluscbor,
      aifplusjson,
      altoCdniplusjson,
      altoCdnifilterplusjson,
      altoCostmapplusjson,
      altoCostmapfilterplusjson,
      altoDirectoryplusjson,
      altoEndpointcostplusjson,
      altoEndpointcostparamsplusjson,
      altoEndpointpropplusjson,
      altoEndpointpropparamsplusjson,
      altoErrorplusjson,
      altoNetworkmapplusjson,
      altoNetworkmapfilterplusjson,
      altoPropmapplusjson,
      altoPropmapparamsplusjson,
      altoTipsplusjson,
      altoTipsparamsplusjson,
      altoUpdatestreamcontrolplusjson,
      altoUpdatestreamparamsplusjson,
      aml,
      andrewInset,
      appinstaller,
      applefile,
      applixware,
      appx,
      appxbundle,
      asyncapiplusjson,
      asyncapiplusyaml,
      atplusjwt,
      atf,
      atfx,
      atomplusxml,
      atomcatplusxml,
      atomdeletedplusxml,
      atomicmail,
      atomsvcplusxml,
      atscDwdplusxml,
      atscDynamicEventMessage,
      atscHeldplusxml,
      atscRdtplusjson,
      atscRsatplusxml,
      atxml,
      authPolicyplusxml,
      automationmlAmlplusxml,
      automationmlAmlxpluszip,
      bacnetXddpluszip,
      batchSmtp,
      bdoc,
      beepplusxml,
      bufr,
      c2pa,
      calendarplusjson,
      calendarplusxml,
      callCompletion,
      cals1840,
      captiveplusjson,
      cbor,
      cborSeq,
      cccex,
      ccmpplusxml,
      ccxmlplusxml,
      cdaplusxml,
      cdfxplusxml,
      cdmiCapability,
      cdmiContainer,
      cdmiDomain,
      cdmiObject,
      cdmiQueue,
      cdni,
      cepluscbor,
      cea,
      cea2018plusxml,
      cellmlplusxml,
      cfw,
      cid,
      cidEdhocpluscborSeq,
      cityplusjson,
      cityplusjsonSeq,
      clr,
      clueplusxml,
      clueInfoplusxml,
      cms,
      cmwpluscbor,
      cmwpluscose,
      cmwplusjson,
      cmwplusjws,
      cnrpplusxml,
      coapEap,
      coapGroupplusjson,
      coapPayload,
      commonground,
      conciseProblemDetailspluscbor,
      conferenceInfoplusxml,
      cose,
      coseKey,
      coseKeySet,
      coseX509,
      cplplusxml,
      csrattrs,
      cstaplusxml,
      cstadataplusxml,
      csvmplusjson,
      cuSeeme,
      cwl,
      cwlplusjson,
      cwlplusyaml,
      cwt,
      cybercash,
      dart,
      dashplusxml,
      dashPatchplusxml,
      dashdelta,
      davmountplusxml,
      dcaRft,
      dcd,
      decDx,
      dialogInfoplusxml,
      dicom,
      dicomplusjson,
      dicomplusxml,
      did,
      dii,
      dit,
      dns,
      dnsplusjson,
      dnsMessage,
      docbookplusxml,
      dotspluscbor,
      dpopplusjwt,
      dskppplusxml,
      dsscplusder,
      dsscplusxml,
      dvcs,
      eatpluscwt,
      eatplusjwt,
      eatBunpluscbor,
      eatBunplusjson,
      eatUcspluscbor,
      eatUcsplusjson,
      ecmascript,
      edhocpluscborSeq,
      ediConsent,
      ediX12,
      edifact,
      efi,
      elmplusjson,
      elmplusxml,
      emergencycalldatadotcapplusxml,
      emergencycalldatadotcommentplusxml,
      emergencycalldatadotcontrolplusxml,
      emergencycalldatadotdeviceinfoplusxml,
      emergencycalldatadotecalldotmsd,
      emergencycalldatadotlegacyesnplusjson,
      emergencycalldatadotproviderinfoplusxml,
      emergencycalldatadotserviceinfoplusxml,
      emergencycalldatadotsubscriberinfoplusxml,
      emergencycalldatadotvedsplusxml,
      emmaplusxml,
      emotionmlplusxml,
      encaprtp,
      entityStatementplusjwt,
      eppplusxml,
      epubpluszip,
      eshop,
      exi,
      expectCtReportplusjson,
      express,
      fastinfoset,
      fastsoap,
      fdf,
      fdtplusxml,
      fhirplusjson,
      fhirplusxml,
      fidodottrustedAppsplusjson,
      fits,
      flexfec,
      fontSfnt,
      fontTdpfr,
      fontWoff,
      frameworkAttributesplusxml,
      geoplusjson,
      geoplusjsonSeq,
      geofeedpluscsv,
      geopackageplussqlite3,
      geoposeplusjson,
      geoxacmlplusjson,
      geoxacmlplusxml,
      gltfBuffer,
      gmlplusxml,
      gnapBindingJws,
      gnapBindingJwsd,
      gnapBindingRotationJws,
      gnapBindingRotationJwsd,
      gpxplusxml,
      grib,
      gxf,
      gzip,
      h224,
      heldplusxml,
      hjson,
      hl7v2plusxml,
      http,
      hyperstudio,
      ibeKeyRequestplusxml,
      ibePkgReplyplusxml,
      ibePpData,
      iges,
      imIscomposingplusxml,
      index,
      indexdotcmd,
      indexdotobj,
      indexdotresponse,
      indexdotvnd,
      inkmlplusxml,
      iotp,
      ipfix,
      ipp,
      isup,
      itsplusxml,
      javaArchive,
      javaSerializedObject,
      javaVm,
      javascript,
      jf2feedplusjson,
      jose,
      joseplusjson,
      jrdplusjson,
      jscalendarplusjson,
      jscontactplusjson,
      json,
      jsonPatchplusjson,
      jsonPatchQueryplusjson,
      jsonSeq,
      json5,
      jsonmlplusjson,
      jsonpath,
      jwkplusjson,
      jwkSetplusjson,
      jwkSetplusjwt,
      jwt,
      kbplusjwt,
      kblplusxml,
      kpmlRequestplusxml,
      kpmlResponseplusxml,
      ldplusjson,
      lgrplusxml,
      linkFormat,
      linkset,
      linksetplusjson,
      loadControlplusxml,
      logoutplusjwt,
      lostplusxml,
      lostsyncplusxml,
      lpfpluszip,
      lxf,
      macBinhex40,
      macCompactpro,
      macwriteii,
      madsplusxml,
      manifestplusjson,
      marc,
      marcxmlplusxml,
      mathematica,
      mathmlplusxml,
      mathmlContentplusxml,
      mathmlPresentationplusxml,
      mbmsAssociatedProcedureDescriptionplusxml,
      mbmsDeregisterplusxml,
      mbmsEnvelopeplusxml,
      mbmsMskplusxml,
      mbmsMskResponseplusxml,
      mbmsProtectionDescriptionplusxml,
      mbmsReceptionReportplusxml,
      mbmsRegisterplusxml,
      mbmsRegisterResponseplusxml,
      mbmsScheduleplusxml,
      mbmsUserServiceDescriptionplusxml,
      mbox,
      mediaPolicyDatasetplusxml,
      mediaControlplusxml,
      mediaservercontrolplusxml,
      mergePatchplusjson,
      metalinkplusxml,
      metalink4plusxml,
      metsplusxml,
      mf4,
      mikey,
      mipc,
      missingBlockspluscborSeq,
      mmtAeiplusxml,
      mmtUsdplusxml,
      modsplusxml,
      mossKeys,
      mossSignature,
      mosskeyData,
      mosskeyRequest,
      mp21,
      mp4,
      mpeg4Generic,
      mpeg4Iod,
      mpeg4IodXmt,
      mrbConsumerplusxml,
      mrbPublishplusxml,
      mscIvrplusxml,
      mscMixerplusxml,
      msix,
      msixbundle,
      msword,
      mudplusjson,
      multipartCore,
      mxf,
      nQuads,
      nTriples,
      nasdata,
      newsCheckgroups,
      newsGroupinfo,
      newsTransmission,
      nlsmlplusxml,
      node,
      nss,
      oauthAuthzReqplusjwt,
      obliviousDnsMessage,
      ocspRequest,
      ocspResponse,
      octetStream,
      oda,
      odmplusxml,
      odx,
      oebpsPackageplusxml,
      ogg,
      ohttpKeys,
      omdocplusxml,
      onenote,
      opcNodesetplusxml,
      oscore,
      oxps,
      p21,
      p21pluszip,
      p2pOverlayplusxml,
      parityfec,
      passport,
      patchOpsErrorplusxml,
      pdf,
      pdx,
      pemCertificateChain,
      pgpEncrypted,
      pgpKeys,
      pgpSignature,
      picsRules,
      pidfplusxml,
      pidfDiffplusxml,
      pkcs10,
      pkcs12,
      pkcs7Mime,
      pkcs7Signature,
      pkcs8,
      pkcs8Encrypted,
      pkixAttrCert,
      pkixCert,
      pkixCrl,
      pkixPkipath,
      pkixcmp,
      plsplusxml,
      pocSettingsplusxml,
      postscript,
      ppspTrackerplusjson,
      privateTokenIssuerDirectory,
      privateTokenRequest,
      privateTokenResponse,
      problemplusjson,
      problemplusxml,
      protobuf,
      protobufplusjson,
      provenanceplusxml,
      providedClaimsplusjwt,
      prsdotalvestranddottitraxSheet,
      prsdotcww,
      prsdotcyn,
      prsdothpubpluszip,
      prsdotimpliedDocumentplusxml,
      prsdotimpliedExecutable,
      prsdotimpliedObjectplusjson,
      prsdotimpliedObjectplusjsonSeq,
      prsdotimpliedObjectplusyaml,
      prsdotimpliedStructure,
      prsdotmayfile,
      prsdotnprend,
      prsdotplucker,
      prsdotrdfXmlCrypt,
      prsdotsclt,
      prsdotvcfbzip2,
      prsdotxsfplusxml,
      pskcplusxml,
      pvdplusjson,
      qsig,
      ramlplusyaml,
      raptorfec,
      rdapplusjson,
      rdfplusxml,
      reginfoplusxml,
      relaxNgCompactSyntax,
      remotePrinting,
      reputonplusjson,
      resolveResponseplusjwt,
      resourceListsplusxml,
      resourceListsDiffplusxml,
      rfcplusxml,
      riscos,
      rlmiplusxml,
      rlsServicesplusxml,
      routeApdplusxml,
      routeSTsidplusxml,
      routeUsdplusxml,
      rpkiChecklist,
      rpkiGhostbusters,
      rpkiManifest,
      rpkiPublication,
      rpkiRoa,
      rpkiSignedTal,
      rpkiUpdown,
      rsMetadataplusxml,
      rsdplusxml,
      rssplusxml,
      rtf,
      rtploopback,
      rtx,
      samlassertionplusxml,
      samlmetadataplusxml,
      sarifplusjson,
      sarifExternalPropertiesplusjson,
      sbe,
      sbmlplusxml,
      scaipplusxml,
      scimplusjson,
      scittReceiptpluscose,
      scittStatementpluscose,
      scvpCvRequest,
      scvpCvResponse,
      scvpVpRequest,
      scvpVpResponse,
      sdJwt,
      sdJwtplusjson,
      sdfplusjson,
      sdp,
      seceventplusjwt,
      senmlpluscbor,
      senmlplusjson,
      senmlplusxml,
      senmlEtchpluscbor,
      senmlEtchplusjson,
      senmlExi,
      sensmlpluscbor,
      sensmlplusjson,
      sensmlplusxml,
      sensmlExi,
      sepplusxml,
      sepExi,
      sessionInfo,
      setPayment,
      setPaymentInitiation,
      setRegistration,
      setRegistrationInitiation,
      sgml,
      sgmlOpenCatalog,
      shfplusxml,
      sieve,
      simpleFilterplusxml,
      simpleMessageSummary,
      simplesymbolcontainer,
      sipc,
      slate,
      smil,
      smilplusxml,
      smpte336m,
      soapplusfastinfoset,
      soapplusxml,
      sparqlQuery,
      sparqlResultsplusxml,
      spdxplusjson,
      spiritsEventplusxml,
      sql,
      srgs,
      srgsplusxml,
      sruplusxml,
      ssdlplusxml,
      sslkeylogfile,
      ssmlplusxml,
      st211041,
      stixplusjson,
      stratum,
      suitEnvelopepluscose,
      suitReportpluscose,
      swidpluscbor,
      swidplusxml,
      tampApexUpdate,
      tampApexUpdateConfirm,
      tampCommunityUpdate,
      tampCommunityUpdateConfirm,
      tampError,
      tampSequenceAdjust,
      tampSequenceAdjustConfirm,
      tampStatusQuery,
      tampStatusResponse,
      tampUpdate,
      tampUpdateConfirm,
      tar,
      taxiiplusjson,
      tdplusjson,
      teiplusxml,
      tetraIsi,
      texinfo,
      thraudplusxml,
      timestampQuery,
      timestampReply,
      timestampedData,
      tlsrptplusgzip,
      tlsrptplusjson,
      tmplusjson,
      tnauthlist,
      tocpluscbor,
      tokenIntrospectionplusjwt,
      toml,
      trickleIceSdpfrag,
      trig,
      trustChainplusjson,
      trustMarkplusjwt,
      trustMarkDelegationplusjwt,
      ttmlplusxml,
      tveTrigger,
      tzif,
      tzifLeap,
      ubjson,
      uccspluscbor,
      ujcsplusjson,
      ulpfec,
      urcGrpsheetplusxml,
      urcRessheetplusxml,
      urcTargetdescplusxml,
      urcUisocketdescplusxml,
      vc,
      vcpluscose,
      vcplusjwt,
      vcplussdJwt,
      vcardplusjson,
      vcardplusxml,
      vecplusxml,
      vecPackageplusgzip,
      vecPackagepluszip,
      vemmi,
      vividencedotscriptfile,
      vnddot1000mindsdotdecisionModelplusxml,
      vnddot1ob,
      vnddot3gppProseplusxml,
      vnddot3gppProsePc3aplusxml,
      vnddot3gppProsePc3achplusxml,
      vnddot3gppProsePc3chplusxml,
      vnddot3gppProsePc8plusxml,
      vnddot3gppV2xLocalServiceInformation,
      vnddot3gppdot5gnas,
      vnddot3gppdot5gsa2x,
      vnddot3gppdot5gsa2xLocalServiceInformation,
      vnddot3gppdot5gsv2x,
      vnddot3gppdot5gsv2xLocalServiceInformation,
      vnddot3gppdotaccessTransferEventsplusxml,
      vnddot3gppdotbsfplusxml,
      vnddot3gppdotcrsplusxml,
      vnddot3gppdotcurrentLocationDiscoveryplusxml,
      vnddot3gppdotgmopplusxml,
      vnddot3gppdotgtpc,
      vnddot3gppdotinterworkingData,
      vnddot3gppdotlpp,
      vnddot3gppdotmcSignallingEar,
      vnddot3gppdotmcdataAffiliationCommandplusxml,
      vnddot3gppdotmcdataInfoplusxml,
      vnddot3gppdotmcdataMsgstoreCtrlRequestplusxml,
      vnddot3gppdotmcdataPayload,
      vnddot3gppdotmcdataRegroupplusxml,
      vnddot3gppdotmcdataServiceConfigplusxml,
      vnddot3gppdotmcdataSignalling,
      vnddot3gppdotmcdataUeConfigplusxml,
      vnddot3gppdotmcdataUserProfileplusxml,
      vnddot3gppdotmcpttAffiliationCommandplusxml,
      vnddot3gppdotmcpttFloorRequestplusxml,
      vnddot3gppdotmcpttInfoplusxml,
      vnddot3gppdotmcpttLocationInfoplusxml,
      vnddot3gppdotmcpttMbmsUsageInfoplusxml,
      vnddot3gppdotmcpttRegroupplusxml,
      vnddot3gppdotmcpttServiceConfigplusxml,
      vnddot3gppdotmcpttSignedplusxml,
      vnddot3gppdotmcpttUeConfigplusxml,
      vnddot3gppdotmcpttUeInitConfigplusxml,
      vnddot3gppdotmcpttUserProfileplusxml,
      vnddot3gppdotmcvideoAffiliationCommandplusxml,
      vnddot3gppdotmcvideoInfoplusxml,
      vnddot3gppdotmcvideoLocationInfoplusxml,
      vnddot3gppdotmcvideoMbmsUsageInfoplusxml,
      vnddot3gppdotmcvideoRegroupplusxml,
      vnddot3gppdotmcvideoServiceConfigplusxml,
      vnddot3gppdotmcvideoTransmissionRequestplusxml,
      vnddot3gppdotmcvideoUeConfigplusxml,
      vnddot3gppdotmcvideoUserProfileplusxml,
      vnddot3gppdotmidCallplusxml,
      vnddot3gppdotngap,
      vnddot3gppdotpfcp,
      vnddot3gppdotpicBwLarge,
      vnddot3gppdotpicBwSmall,
      vnddot3gppdotpicBwVar,
      vnddot3gppdotpinappInfoplusxml,
      vnddot3gppdots1ap,
      vnddot3gppdotsealAppCommRequirementsInfoplusxml,
      vnddot3gppdotsealDataDeliveryInfopluscbor,
      vnddot3gppdotsealDataDeliveryInfoplusxml,
      vnddot3gppdotsealGroupDocplusxml,
      vnddot3gppdotsealInfoplusxml,
      vnddot3gppdotsealLocationInfopluscbor,
      vnddot3gppdotsealLocationInfoplusxml,
      vnddot3gppdotsealMbmsUsageInfoplusxml,
      vnddot3gppdotsealMbsUsageInfoplusxml,
      vnddot3gppdotsealNetworkQosManagementInfoplusxml,
      vnddot3gppdotsealNetworkResourceInfopluscbor,
      vnddot3gppdotsealUeConfigInfoplusxml,
      vnddot3gppdotsealUnicastInfoplusxml,
      vnddot3gppdotsealUserProfileInfoplusxml,
      vnddot3gppdotsms,
      vnddot3gppdotsmsplusxml,
      vnddot3gppdotsrvccExtplusxml,
      vnddot3gppdotsrvccInfoplusxml,
      vnddot3gppdotstateAndEventInfoplusxml,
      vnddot3gppdotussdplusxml,
      vnddot3gppdotv2x,
      vnddot3gppdotvaeInfoplusxml,
      vnddot3gpp2dotbcmcsinfoplusxml,
      vnddot3gpp2dotsms,
      vnddot3gpp2dottcap,
      vnddot3lightssoftwaredotimagescal,
      vnddot3mdotpostItNotes,
      vnddotaccpacdotsimplydotaso,
      vnddotaccpacdotsimplydotimp,
      vnddotacmdotaddressxferplusjson,
      vnddotacmdotchatbotplusjson,
      vnddotacucobol,
      vnddotacucorp,
      vnddotadobedotairApplicationInstallerPackagepluszip,
      vnddotadobedotflashdotmovie,
      vnddotadobedotformscentraldotfcdt,
      vnddotadobedotfxp,
      vnddotadobedotpartialUpload,
      vnddotadobedotxdpplusxml,
      vnddotadobedotxfdf,
      vnddotaetherdotimp,
      vnddotafpcdotafplinedata,
      vnddotafpcdotafplinedataPagedef,
      vnddotafpcdotcmocaCmresource,
      vnddotafpcdotfocaCharset,
      vnddotafpcdotfocaCodedfont,
      vnddotafpcdotfocaCodepage,
      vnddotafpcdotmodca,
      vnddotafpcdotmodcaCmtable,
      vnddotafpcdotmodcaFormdef,
      vnddotafpcdotmodcaMediummap,
      vnddotafpcdotmodcaObjectcontainer,
      vnddotafpcdotmodcaOverlay,
      vnddotafpcdotmodcaPagesegment,
      vnddotage,
      vnddotahBarcode,
      vnddotaheaddotspace,
      vnddotaia,
      vnddotairzipdotfilesecuredotazf,
      vnddotairzipdotfilesecuredotazs,
      vnddotamadeusplusjson,
      vnddotamazondotebook,
      vnddotamazondotmobi8Ebook,
      vnddotamericandynamicsdotacc,
      vnddotamigadotami,
      vnddotamundsendotmazeplusxml,
      vnddotandroiddotota,
      vnddotandroiddotpackageArchive,
      vnddotanki,
      vnddotanserWebCertificateIssueInitiation,
      vnddotanserWebFundsTransferInitiation,
      vnddotantixdotgameComponent,
      vnddotapachedotarrowdotfile,
      vnddotapachedotarrowdotstream,
      vnddotapachedotparquet,
      vnddotapachedotthriftdotbinary,
      vnddotapachedotthriftdotcompact,
      vnddotapachedotthriftdotjson,
      vnddotapexlang,
      vnddotapiplusjson,
      vnddotaplextordotwarrpplusjson,
      vnddotapothekendedotreservationplusjson,
      vnddotappledotinstallerplusxml,
      vnddotappledotkeynote,
      vnddotappledotmpegurl,
      vnddotappledotnumbers,
      vnddotappledotpages,
      vnddotappledotpkpass,
      vnddotarastradotswi,
      vnddotaristanetworksdotswi,
      vnddotartisanplusjson,
      vnddotartsquare,
      vnddotas207960dotvasdotconfigplusjer,
      vnddotas207960dotvasdotconfigplusuper,
      vnddotas207960dotvasdottapplusjer,
      vnddotas207960dotvasdottapplusuper,
      vnddotastraeaSoftwaredotiota,
      vnddotaudiograph,
      vnddotautodeskdotfbx,
      vnddotautopackage,
      vnddotavalonplusjson,
      vnddotavistarplusxml,
      vnddotbalsamiqdotbmmlplusxml,
      vnddotbalsamiqdotbmpr,
      vnddotbananaAccounting,
      vnddotbbfdotuspdoterror,
      vnddotbbfdotuspdotmsg,
      vnddotbbfdotuspdotmsgplusjson,
      vnddotbekitzurStechplusjson,
      vnddotbelightsoftdotlhzdpluszip,
      vnddotbelightsoftdotlhzlpluszip,
      vnddotbintdotmedContent,
      vnddotbiopaxdotrdfplusxml,
      vnddotblinkIdbValueWrapper,
      vnddotblueicedotmultipass,
      vnddotbluetoothdotepdotoob,
      vnddotbluetoothdotledotoob,
      vnddotbmi,
      vnddotbpf,
      vnddotbpf3,
      vnddotbusinessobjects,
      vnddotbyudotuapiplusjson,
      vnddotbzip3,
      vnddotc3vocdotscheduleplusxml,
      vnddotcabJscript,
      vnddotcanonCpdl,
      vnddotcanonLips,
      vnddotcapasystemsPgplusjson,
      vnddotcel,
      vnddotcendiodotthinlincdotclientconf,
      vnddotcenturySystemsdottcpStream,
      vnddotchemdrawplusxml,
      vnddotchessPgn,
      vnddotchipnutsdotkaraokeMmd,
      vnddotciedi,
      vnddotcinderella,
      vnddotcirpackdotisdnExt,
      vnddotcitationstylesdotstyleplusxml,
      vnddotclaymore,
      vnddotcloantodotrp9,
      vnddotclonkdotc4group,
      vnddotcluetrustdotcartomobileConfig,
      vnddotcluetrustdotcartomobileConfigPkg,
      vnddotcncfdothelmdotchartdotcontentdotv1dottarplusgzip,
      vnddotcncfdothelmdotchartdotprovenancedotv1dotprov,
      vnddotcncfdothelmdotconfigdotv1plusjson,
      vnddotcoffeescript,
      vnddotcollabiodotxodocumentsdotdocument,
      vnddotcollabiodotxodocumentsdotdocumentTemplate,
      vnddotcollabiodotxodocumentsdotpresentation,
      vnddotcollabiodotxodocumentsdotpresentationTemplate,
      vnddotcollabiodotxodocumentsdotspreadsheet,
      vnddotcollabiodotxodocumentsdotspreadsheetTemplate,
      vnddotcollectionplusjson,
      vnddotcollectiondotdocplusjson,
      vnddotcollectiondotnextplusjson,
      vnddotcomicbookpluszip,
      vnddotcomicbookRar,
      vnddotcommerceBattelle,
      vnddotcommonspace,
      vnddotcontactdotcmsg,
      vnddotcoreosdotignitionplusjson,
      vnddotcosmocaller,
      vnddotcrickdotclicker,
      vnddotcrickdotclickerdotkeyboard,
      vnddotcrickdotclickerdotpalette,
      vnddotcrickdotclickerdottemplate,
      vnddotcrickdotclickerdotwordbank,
      vnddotcriticaltoolsdotwbsplusxml,
      vnddotcryptiidotpipeplusjson,
      vnddotcryptoShadeFile,
      vnddotcryptomatordotencrypted,
      vnddotcryptomatordotvault,
      vnddotctcPosml,
      vnddotctctdotwsplusxml,
      vnddotcupsPdf,
      vnddotcupsPostscript,
      vnddotcupsPpd,
      vnddotcupsRaster,
      vnddotcupsRaw,
      vnddotcurl,
      vnddotcurldotcar,
      vnddotcurldotpcurl,
      vnddotcyandotdeandotrootplusxml,
      vnddotcybank,
      vnddotcyclonedxplusjson,
      vnddotcyclonedxplusxml,
      vnddotd2ldotcoursepackage1p0pluszip,
      vnddotd3mDataset,
      vnddotd3mProblem,
      vnddotdart,
      vnddotdataVisiondotrdz,
      vnddotdatalog,
      vnddotdatapackageplusjson,
      vnddotdataresourceplusjson,
      vnddotdbf,
      vnddotdcmpplusxml,
      vnddotdebiandotbinaryPackage,
      vnddotdecedotdata,
      vnddotdecedotttmlplusxml,
      vnddotdecedotunspecified,
      vnddotdecedotzip,
      vnddotdenovodotfcselayoutLink,
      vnddotdesmumedotmovie,
      vnddotdirBidotplateDlNosuffix,
      vnddotdmdotdelegationplusxml,
      vnddotdna,
      vnddotdocumentplusjson,
      vnddotdolbydotmlp,
      vnddotdolbydotmobiledot1,
      vnddotdolbydotmobiledot2,
      vnddotdoremirdotscorecloudBinaryDocument,
      vnddotdpgraph,
      vnddotdreamfactory,
      vnddotdriveplusjson,
      vnddotdsKeypoint,
      vnddotdtgdotlocal,
      vnddotdtgdotlocaldotflash,
      vnddotdtgdotlocaldothtml,
      vnddotdvbdotait,
      vnddotdvbdotdvbislplusxml,
      vnddotdvbdotdvbj,
      vnddotdvbdotesgcontainer,
      vnddotdvbdotipdcdftnotifaccess,
      vnddotdvbdotipdcesgaccess,
      vnddotdvbdotipdcesgaccess2,
      vnddotdvbdotipdcesgpdd,
      vnddotdvbdotipdcroaming,
      vnddotdvbdotiptvdotalfecBase,
      vnddotdvbdotiptvdotalfecEnhancement,
      vnddotdvbdotnotifAggregateRootplusxml,
      vnddotdvbdotnotifContainerplusxml,
      vnddotdvbdotnotifGenericplusxml,
      vnddotdvbdotnotifIaMsglistplusxml,
      vnddotdvbdotnotifIaRegistrationRequestplusxml,
      vnddotdvbdotnotifIaRegistrationResponseplusxml,
      vnddotdvbdotnotifInitplusxml,
      vnddotdvbdotpfr,
      vnddotdvbdotservice,
      vnddotdxr,
      vnddotdynageo,
      vnddotdzr,
      vnddoteasykaraokedotcdgdownload,
      vnddotecdisUpdate,
      vnddotecipdotrlp,
      vnddoteclipsedotdittoplusjson,
      vnddotecowindotchart,
      vnddotecowindotfilerequest,
      vnddotecowindotfileupdate,
      vnddotecowindotseries,
      vnddotecowindotseriesrequest,
      vnddotecowindotseriesupdate,
      vnddotefidotimg,
      vnddotefidotiso,
      vnddotelnpluszip,
      vnddotemclientdotaccessrequestplusxml,
      vnddotenliven,
      vnddotenphasedotenvoy,
      vnddoteprintsdotdataplusxml,
      vnddotepsondotesf,
      vnddotepsondotmsf,
      vnddotepsondotquickanime,
      vnddotepsondotsalt,
      vnddotepsondotssf,
      vnddotericssondotquickcall,
      vnddoterofs,
      vnddotespassEspasspluszip,
      vnddoteszigno3plusxml,
      vnddotetsidotaocplusxml,
      vnddotetsidotasicEpluszip,
      vnddotetsidotasicSpluszip,
      vnddotetsidotcugplusxml,
      vnddotetsidotiptvcommandplusxml,
      vnddotetsidotiptvdiscoveryplusxml,
      vnddotetsidotiptvprofileplusxml,
      vnddotetsidotiptvsadBcplusxml,
      vnddotetsidotiptvsadCodplusxml,
      vnddotetsidotiptvsadNpvrplusxml,
      vnddotetsidotiptvserviceplusxml,
      vnddotetsidotiptvsyncplusxml,
      vnddotetsidotiptvueprofileplusxml,
      vnddotetsidotmcidplusxml,
      vnddotetsidotmheg5,
      vnddotetsidotoverloadControlPolicyDatasetplusxml,
      vnddotetsidotpstnplusxml,
      vnddotetsidotsciplusxml,
      vnddotetsidotsimservsplusxml,
      vnddotetsidottimestampToken,
      vnddotetsidottslplusxml,
      vnddotetsidottsldotder,
      vnddoteudotkaspariandotcarplusjson,
      vnddoteudoradotdata,
      vnddotevolvdotecigdotprofile,
      vnddotevolvdotecigdotsettings,
      vnddotevolvdotecigdottheme,
      vnddotexstreamEmpowerpluszip,
      vnddotexstreamPackage,
      vnddotezpixAlbum,
      vnddotezpixPackage,
      vnddotfSecuredotmobile,
      vnddotfafplusyaml,
      vnddotfamilysearchdotgedcompluszip,
      vnddotfastcopyDiskImage,
      vnddotfdf,
      vnddotfdsndotmseed,
      vnddotfdsndotseed,
      vnddotfdsndotstationxmlplusxml,
      vnddotffsns,
      vnddotfgb,
      vnddotficlabdotflbpluszip,
      vnddotfilmitdotzfc,
      vnddotfints,
      vnddotfiremonkeysdotcloudcell,
      vnddotflographit,
      vnddotfluxtimedotclip,
      vnddotfontFontforgeSfd,
      vnddotframemaker,
      vnddotfreelogdotcomic,
      vnddotfrogansdotfnc,
      vnddotfrogansdotltf,
      vnddotfscdotweblaunch,
      vnddotfujifilmdotfbdotdocuworks,
      vnddotfujifilmdotfbdotdocuworksdotbinder,
      vnddotfujifilmdotfbdotdocuworksdotcontainer,
      vnddotfujifilmdotfbdotjfiplusxml,
      vnddotfujitsudotoasys,
      vnddotfujitsudotoasys2,
      vnddotfujitsudotoasys3,
      vnddotfujitsudotoasysgp,
      vnddotfujitsudotoasysprs,
      vnddotfujixeroxdotartEx,
      vnddotfujixeroxdotart4,
      vnddotfujixeroxdotddd,
      vnddotfujixeroxdotdocuworks,
      vnddotfujixeroxdotdocuworksdotbinder,
      vnddotfujixeroxdotdocuworksdotcontainer,
      vnddotfujixeroxdothbpl,
      vnddotfutMisnet,
      vnddotfutoinpluscbor,
      vnddotfutoinplusjson,
      vnddotfuzzysheet,
      vnddotg3pixdotg3fc,
      vnddotga4ghdotpassportplusjwt,
      vnddotgenomatixdottuxedo,
      vnddotgenozip,
      vnddotgenticsdotgrdplusjson,
      vnddotgentoodotcatmetadataplusxml,
      vnddotgentoodotebuild,
      vnddotgentoodoteclass,
      vnddotgentoodotgpkg,
      vnddotgentoodotmanifest,
      vnddotgentoodotpkgmetadataplusxml,
      vnddotgentoodotxpak,
      vnddotgeoplusjson,
      vnddotgeocubeplusxml,
      vnddotgeogebradotfile,
      vnddotgeogebradotpinboard,
      vnddotgeogebradotslides,
      vnddotgeogebradottool,
      vnddotgeometryExplorer,
      vnddotgeonext,
      vnddotgeoplan,
      vnddotgeospace,
      vnddotgerber,
      vnddotglobalplatformdotcardContentMgt,
      vnddotglobalplatformdotcardContentMgtResponse,
      vnddotgmx,
      vnddotgnudottalerdotexchangeplusjson,
      vnddotgnudottalerdotmerchantplusjson,
      vnddotgoogleAppsdotaudio,
      vnddotgoogleAppsdotdocument,
      vnddotgoogleAppsdotdrawing,
      vnddotgoogleAppsdotdriveSdk,
      vnddotgoogleAppsdotfile,
      vnddotgoogleAppsdotfolder,
      vnddotgoogleAppsdotform,
      vnddotgoogleAppsdotfusiontable,
      vnddotgoogleAppsdotjam,
      vnddotgoogleAppsdotmailLayout,
      vnddotgoogleAppsdotmap,
      vnddotgoogleAppsdotphoto,
      vnddotgoogleAppsdotpresentation,
      vnddotgoogleAppsdotscript,
      vnddotgoogleAppsdotshortcut,
      vnddotgoogleAppsdotsite,
      vnddotgoogleAppsdotspreadsheet,
      vnddotgoogleAppsdotunknown,
      vnddotgoogleAppsdotvideo,
      vnddotgoogleEarthdotkmlplusxml,
      vnddotgoogleEarthdotkmz,
      vnddotgovdotskdoteFormplusxml,
      vnddotgovdotskdoteFormpluszip,
      vnddotgovdotskdotxmldatacontainerplusxml,
      vnddotgpxseedotmapplusxml,
      vnddotgrafeq,
      vnddotgridmp,
      vnddotgrooveAccount,
      vnddotgrooveHelp,
      vnddotgrooveIdentityMessage,
      vnddotgrooveInjector,
      vnddotgrooveToolMessage,
      vnddotgrooveToolTemplate,
      vnddotgrooveVcard,
      vnddothalplusjson,
      vnddothalplusxml,
      vnddothandheldEntertainmentplusxml,
      vnddothbci,
      vnddothcplusjson,
      vnddothclBireports,
      vnddothdt,
      vnddotherokuplusjson,
      vnddothhedotlessonPlayer,
      vnddothpHpgl,
      vnddothpHpid,
      vnddothpHps,
      vnddothpJlyt,
      vnddothpPcl,
      vnddothpPclxl,
      vnddothsl,
      vnddothttphone,
      vnddothydrostatixdotsofData,
      vnddothyperplusjson,
      vnddothyperItemplusjson,
      vnddothyperdriveplusjson,
      vnddothzn3dCrossword,
      vnddotibmdotafplinedata,
      vnddotibmdotelectronicMedia,
      vnddotibmdotminipay,
      vnddotibmdotmodcap,
      vnddotibmdotrightsManagement,
      vnddotibmdotsecureContainer,
      vnddoticcprofile,
      vnddotieeedot1905,
      vnddotigloader,
      vnddotimagemeterdotfolderpluszip,
      vnddotimagemeterdotimagepluszip,
      vnddotimmervisionIvp,
      vnddotimmervisionIvu,
      vnddotimsdotimsccv1p1,
      vnddotimsdotimsccv1p2,
      vnddotimsdotimsccv1p3,
      vnddotimsdotlisdotv2dotresultplusjson,
      vnddotimsdotltidotv2dottoolconsumerprofileplusjson,
      vnddotimsdotltidotv2dottoolproxyplusjson,
      vnddotimsdotltidotv2dottoolproxydotidplusjson,
      vnddotimsdotltidotv2dottoolsettingsplusjson,
      vnddotimsdotltidotv2dottoolsettingsdotsimpleplusjson,
      vnddotinformedcontroldotrmsplusxml,
      vnddotinformixVisionary,
      vnddotinfotechdotproject,
      vnddotinfotechdotprojectplusxml,
      vnddotinnopathdotwampdotnotification,
      vnddotinsorsdotigm,
      vnddotintercondotformnet,
      vnddotintergeo,
      vnddotintertrustdotdigibox,
      vnddotintertrustdotnncp,
      vnddotintudotqbo,
      vnddotintudotqfx,
      vnddotipfsdotipnsRecord,
      vnddotiplddotcar,
      vnddotiplddotdagCbor,
      vnddotiplddotdagJson,
      vnddotiplddotraw,
      vnddotiptcdotg2dotcatalogitemplusxml,
      vnddotiptcdotg2dotconceptitemplusxml,
      vnddotiptcdotg2dotknowledgeitemplusxml,
      vnddotiptcdotg2dotnewsitemplusxml,
      vnddotiptcdotg2dotnewsmessageplusxml,
      vnddotiptcdotg2dotpackageitemplusxml,
      vnddotiptcdotg2dotplanningitemplusxml,
      vnddotipunpluggeddotrcprofile,
      vnddotirepositorydotpackageplusxml,
      vnddotisXpr,
      vnddotisacdotfcs,
      vnddotiso1178310pluszip,
      vnddotjam,
      vnddotjapannetDirectoryService,
      vnddotjapannetJpnstoreWakeup,
      vnddotjapannetPaymentWakeup,
      vnddotjapannetRegistration,
      vnddotjapannetRegistrationWakeup,
      vnddotjapannetSetstoreWakeup,
      vnddotjapannetVerification,
      vnddotjapannetVerificationWakeup,
      vnddotjcpdotjavamedotmidletRms,
      vnddotjisp,
      vnddotjoostdotjodaArchive,
      vnddotjskdotisdnNgn,
      vnddotkahootz,
      vnddotkdedotkarbon,
      vnddotkdedotkchart,
      vnddotkdedotkformula,
      vnddotkdedotkivio,
      vnddotkdedotkontour,
      vnddotkdedotkpresenter,
      vnddotkdedotkspread,
      vnddotkdedotkword,
      vnddotkdl,
      vnddotkenameaapp,
      vnddotkeymandotkmppluszip,
      vnddotkeymandotkmx,
      vnddotkidspiration,
      vnddotkinar,
      vnddotkoan,
      vnddotkodakDescriptor,
      vnddotlas,
      vnddotlasdotlasplusjson,
      vnddotlasdotlasplusxml,
      vnddotlaszip,
      vnddotldevdotproductlicensing,
      vnddotleapplusjson,
      vnddotlibertyRequestplusxml,
      vnddotllamagraphicsdotlifeBalancedotdesktop,
      vnddotllamagraphicsdotlifeBalancedotexchangeplusxml,
      vnddotlogipipedotcircuitpluszip,
      vnddotloom,
      vnddotlotus123,
      vnddotlotusApproach,
      vnddotlotusFreelance,
      vnddotlotusNotes,
      vnddotlotusOrganizer,
      vnddotlotusScreencam,
      vnddotlotusWordpro,
      vnddotmacportsdotportpkg,
      vnddotmaml,
      vnddotmapboxVectorTile,
      vnddotmarlindotdrmdotactiontokenplusxml,
      vnddotmarlindotdrmdotconftokenplusxml,
      vnddotmarlindotdrmdotlicenseplusxml,
      vnddotmarlindotdrmdotmdcf,
      vnddotmasonplusjson,
      vnddotmaxardotarchivedot3tzpluszip,
      vnddotmaxminddotmaxmindDb,
      vnddotmcd,
      vnddotmdl,
      vnddotmdlMbsdf,
      vnddotmedcalcdata,
      vnddotmediastationdotcdkey,
      vnddotmedicalholodeckdotrecordxr,
      vnddotmeridianSlingshot,
      vnddotmermaid,
      vnddotmfer,
      vnddotmfmp,
      vnddotmicroplusjson,
      vnddotmicrografxdotflo,
      vnddotmicrografxdotigx,
      vnddotmicrosoftdotportableExecutable,
      vnddotmicrosoftdotwindowsdotthumbnailCache,
      vnddotmieleplusjson,
      vnddotmif,
      vnddotminisoftHp3000Save,
      vnddotmitsubishidotmistyGuarddottrustweb,
      vnddotmobiusdotdaf,
      vnddotmobiusdotdis,
      vnddotmobiusdotmbk,
      vnddotmobiusdotmqy,
      vnddotmobiusdotmsl,
      vnddotmobiusdotplc,
      vnddotmobiusdottxf,
      vnddotmodl,
      vnddotmophundotapplication,
      vnddotmophundotcertificate,
      vnddotmotoroladotflexsuite,
      vnddotmotoroladotflexsuitedotadsi,
      vnddotmotoroladotflexsuitedotfis,
      vnddotmotoroladotflexsuitedotgotap,
      vnddotmotoroladotflexsuitedotkmr,
      vnddotmotoroladotflexsuitedotttc,
      vnddotmotoroladotflexsuitedotwem,
      vnddotmotoroladotiprm,
      vnddotmozilladotxulplusxml,
      vnddotms3mfdocument,
      vnddotmsArtgalry,
      vnddotmsAsf,
      vnddotmsCabCompressed,
      vnddotmsColordoticcprofile,
      vnddotmsExcel,
      vnddotmsExceldotaddindotmacroenableddot12,
      vnddotmsExceldotsheetdotbinarydotmacroenableddot12,
      vnddotmsExceldotsheetdotmacroenableddot12,
      vnddotmsExceldottemplatedotmacroenableddot12,
      vnddotmsFontobject,
      vnddotmsHtmlhelp,
      vnddotmsIms,
      vnddotmsLrm,
      vnddotmsOfficedotactivexplusxml,
      vnddotmsOfficetheme,
      vnddotmsOpentype,
      vnddotmsOutlook,
      vnddotmsPackagedotobfuscatedOpentype,
      vnddotmsPkidotseccat,
      vnddotmsPkidotstl,
      vnddotmsPlayreadydotinitiatorplusxml,
      vnddotmsPowerpoint,
      vnddotmsPowerpointdotaddindotmacroenableddot12,
      vnddotmsPowerpointdotpresentationdotmacroenableddot12,
      vnddotmsPowerpointdotslidedotmacroenableddot12,
      vnddotmsPowerpointdotslideshowdotmacroenableddot12,
      vnddotmsPowerpointdottemplatedotmacroenableddot12,
      vnddotmsPrintdevicecapabilitiesplusxml,
      vnddotmsPrintingdotprintticketplusxml,
      vnddotmsPrintschematicketplusxml,
      vnddotmsProject,
      vnddotmsTnef,
      vnddotmsVisiodotviewer,
      vnddotmsWindowsdotdevicepairing,
      vnddotmsWindowsdotnwprintingdotoob,
      vnddotmsWindowsdotprinterpairing,
      vnddotmsWindowsdotwsddotoob,
      vnddotmsWmdrmdotlicChlgReq,
      vnddotmsWmdrmdotlicResp,
      vnddotmsWmdrmdotmeterChlgReq,
      vnddotmsWmdrmdotmeterResp,
      vnddotmsWorddotdocumentdotmacroenableddot12,
      vnddotmsWorddottemplatedotmacroenableddot12,
      vnddotmsWorks,
      vnddotmsWpl,
      vnddotmsXpsdocument,
      vnddotmsaDiskImage,
      vnddotmseq,
      vnddotmsgpack,
      vnddotmsign,
      vnddotmultiaddotcreator,
      vnddotmultiaddotcreatordotcif,
      vnddotmusicNiff,
      vnddotmusician,
      vnddotmuveedotstyle,
      vnddotmynfc,
      vnddotnacamardotybridplusjson,
      vnddotnatodotbindingdataobjectpluscbor,
      vnddotnatodotbindingdataobjectplusjson,
      vnddotnatodotbindingdataobjectplusxml,
      vnddotnatodotopenxmlformatsPackagedotiepdpluszip,
      vnddotncddotcontrol,
      vnddotncddotreference,
      vnddotnearstdotinvplusjson,
      vnddotnebuminddotline,
      vnddotnervana,
      vnddotnetfpx,
      vnddotneurolanguagedotnlu,
      vnddotnimn,
      vnddotnintendodotnitrodotrom,
      vnddotnintendodotsnesdotrom,
      vnddotnitf,
      vnddotnoblenetDirectory,
      vnddotnoblenetSealer,
      vnddotnoblenetWeb,
      vnddotnokiadotcatalogs,
      vnddotnokiadotconmlpluswbxml,
      vnddotnokiadotconmlplusxml,
      vnddotnokiadotiptvdotconfigplusxml,
      vnddotnokiadotisdsRadioPresets,
      vnddotnokiadotlandmarkpluswbxml,
      vnddotnokiadotlandmarkplusxml,
      vnddotnokiadotlandmarkcollectionplusxml,
      vnddotnokiadotnGagedotacplusxml,
      vnddotnokiadotnGagedotdata,
      vnddotnokiadotnGagedotsymbiandotinstall,
      vnddotnokiadotncd,
      vnddotnokiadotpcdpluswbxml,
      vnddotnokiadotpcdplusxml,
      vnddotnokiadotradioPreset,
      vnddotnokiadotradioPresets,
      vnddotnovadigmdotedm,
      vnddotnovadigmdotedx,
      vnddotnovadigmdotext,
      vnddotnttLocaldotcontentShare,
      vnddotnttLocaldotfileTransfer,
      vnddotnttLocaldotogwRemoteAccess,
      vnddotnttLocaldotsipTaRemote,
      vnddotnttLocaldotsipTaTcpStream,
      vnddotnubaltecdotnudokuGame,
      vnddotoaidotworkflows,
      vnddotoaidotworkflowsplusjson,
      vnddotoaidotworkflowsplusyaml,
      vnddotoasisdotopendocumentdotbase,
      vnddotoasisdotopendocumentdotchart,
      vnddotoasisdotopendocumentdotchartTemplate,
      vnddotoasisdotopendocumentdotdatabase,
      vnddotoasisdotopendocumentdotformula,
      vnddotoasisdotopendocumentdotformulaTemplate,
      vnddotoasisdotopendocumentdotgraphics,
      vnddotoasisdotopendocumentdotgraphicsTemplate,
      vnddotoasisdotopendocumentdotimage,
      vnddotoasisdotopendocumentdotimageTemplate,
      vnddotoasisdotopendocumentdotpresentation,
      vnddotoasisdotopendocumentdotpresentationTemplate,
      vnddotoasisdotopendocumentdotspreadsheet,
      vnddotoasisdotopendocumentdotspreadsheetTemplate,
      vnddotoasisdotopendocumentdottext,
      vnddotoasisdotopendocumentdottextMaster,
      vnddotoasisdotopendocumentdottextMasterTemplate,
      vnddotoasisdotopendocumentdottextTemplate,
      vnddotoasisdotopendocumentdottextWeb,
      vnddotobn,
      vnddotocfpluscbor,
      vnddotocidotimagedotmanifestdotv1plusjson,
      vnddotoftndotl10nplusjson,
      vnddotoipfdotcontentaccessdownloadplusxml,
      vnddotoipfdotcontentaccessstreamingplusxml,
      vnddotoipfdotcspgHexbinary,
      vnddotoipfdotdaedotsvgplusxml,
      vnddotoipfdotdaedotxhtmlplusxml,
      vnddotoipfdotmippvcontrolmessageplusxml,
      vnddotoipfdotpaedotgem,
      vnddotoipfdotspdiscoveryplusxml,
      vnddotoipfdotspdlistplusxml,
      vnddotoipfdotueprofileplusxml,
      vnddotoipfdotuserprofileplusxml,
      vnddotolpcSugar,
      vnddotomaScwsConfig,
      vnddotomaScwsHttpRequest,
      vnddotomaScwsHttpResponse,
      vnddotomadotbcastdotassociatedProcedureParameterplusxml,
      vnddotomadotbcastdotdrmTriggerplusxml,
      vnddotomadotbcastdotimdplusxml,
      vnddotomadotbcastdotltkm,
      vnddotomadotbcastdotnotificationplusxml,
      vnddotomadotbcastdotprovisioningtrigger,
      vnddotomadotbcastdotsgboot,
      vnddotomadotbcastdotsgddplusxml,
      vnddotomadotbcastdotsgdu,
      vnddotomadotbcastdotsimpleSymbolContainer,
      vnddotomadotbcastdotsmartcardTriggerplusxml,
      vnddotomadotbcastdotsprovplusxml,
      vnddotomadotbcastdotstkm,
      vnddotomadotcabAddressBookplusxml,
      vnddotomadotcabFeatureHandlerplusxml,
      vnddotomadotcabPccplusxml,
      vnddotomadotcabSubsInviteplusxml,
      vnddotomadotcabUserPrefsplusxml,
      vnddotomadotdcd,
      vnddotomadotdcdc,
      vnddotomadotdd2plusxml,
      vnddotomadotdrmdotrisdplusxml,
      vnddotomadotgroupUsageListplusxml,
      vnddotomadotlwm2mpluscbor,
      vnddotomadotlwm2mplusjson,
      vnddotomadotlwm2mplustlv,
      vnddotomadotpalplusxml,
      vnddotomadotpocdotdetailedProgressReportplusxml,
      vnddotomadotpocdotfinalReportplusxml,
      vnddotomadotpocdotgroupsplusxml,
      vnddotomadotpocdotinvocationDescriptorplusxml,
      vnddotomadotpocdotoptimizedProgressReportplusxml,
      vnddotomadotpush,
      vnddotomadotscidmdotmessagesplusxml,
      vnddotomadotxcapDirectoryplusxml,
      vnddotomadsEmailplusxml,
      vnddotomadsFileplusxml,
      vnddotomadsFolderplusxml,
      vnddotomalocSuplInit,
      vnddotomsdotcellularCoseContentpluscbor,
      vnddotonepager,
      vnddotonepagertamp,
      vnddotonepagertamx,
      vnddotonepagertat,
      vnddotonepagertatp,
      vnddotonepagertatx,
      vnddotonvifdotmetadata,
      vnddotopenbloxdotgameplusxml,
      vnddotopenbloxdotgameBinary,
      vnddotopeneyedotoeb,
      vnddotopenofficeorgdotextension,
      vnddotopenprinttag,
      vnddotopenstreetmapdotdataplusxml,
      vnddotopentimestampsdotots,
      vnddotopenvpidotdspxplusjson,
      vnddotopenxmlformatsOfficedocumentdotcustomPropertiesplusxml,
      vnddotopenxmlformatsOfficedocumentdotcustomxmlpropertiesplusxml,
      vnddotopenxmlformatsOfficedocumentdotdrawingplusxml,
      vnddotopenxmlformatsOfficedocumentdotdrawingmldotchartplusxml,
      vnddotopenxmlformatsOfficedocumentdotdrawingmldotchartshapesplusxml,
      vnddotopenxmlformatsOfficedocumentdotdrawingmldotdiagramcolorsplusxml,
      vnddotopenxmlformatsOfficedocumentdotdrawingmldotdiagramdataplusxml,
      vnddotopenxmlformatsOfficedocumentdotdrawingmldotdiagramlayoutplusxml,
      vnddotopenxmlformatsOfficedocumentdotdrawingmldotdiagramstyleplusxml,
      vnddotopenxmlformatsOfficedocumentdotextendedPropertiesplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotcommentauthorsplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotcommentsplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldothandoutmasterplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotnotesmasterplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotnotesslideplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotpresentation,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotpresentationdotmainplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotprespropsplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotslide,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotslideplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotslidelayoutplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotslidemasterplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotslideshow,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotslideshowdotmainplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotslideupdateinfoplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldottablestylesplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldottagsplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldottemplate,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldottemplatedotmainplusxml,
      vnddotopenxmlformatsOfficedocumentdotpresentationmldotviewpropsplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotcalcchainplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotchartsheetplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotcommentsplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotconnectionsplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotdialogsheetplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotexternallinkplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotpivotcachedefinitionplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotpivotcacherecordsplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotpivottableplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotquerytableplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotrevisionheadersplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotrevisionlogplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotsharedstringsplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotsheet,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotsheetdotmainplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotsheetmetadataplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotstylesplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldottableplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldottablesinglecellsplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldottemplate,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldottemplatedotmainplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotusernamesplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotvolatiledependenciesplusxml,
      vnddotopenxmlformatsOfficedocumentdotspreadsheetmldotworksheetplusxml,
      vnddotopenxmlformatsOfficedocumentdotthemeplusxml,
      vnddotopenxmlformatsOfficedocumentdotthemeoverrideplusxml,
      vnddotopenxmlformatsOfficedocumentdotvmldrawing,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotcommentsplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotdocument,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotdocumentdotglossaryplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotdocumentdotmainplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotendnotesplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotfonttableplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotfooterplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotfootnotesplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotnumberingplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotsettingsplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotstylesplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldottemplate,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldottemplatedotmainplusxml,
      vnddotopenxmlformatsOfficedocumentdotwordprocessingmldotwebsettingsplusxml,
      vnddotopenxmlformatsPackagedotcorePropertiesplusxml,
      vnddotopenxmlformatsPackagedotdigitalSignatureXmlsignatureplusxml,
      vnddotopenxmlformatsPackagedotrelationshipsplusxml,
      vnddotoracledotresourceplusjson,
      vnddotorangedotindata,
      vnddotosadotnetdeploy,
      vnddotosgeodotmapguidedotpackage,
      vnddotosgidotbundle,
      vnddotosgidotdp,
      vnddotosgidotsubsystem,
      vnddototpsdotctKipplusxml,
      vnddotoxlidotcountgraph,
      vnddotpagerdutyplusjson,
      vnddotpalm,
      vnddotpanoply,
      vnddotpaosdotxml,
      vnddotpatentdive,
      vnddotpatientecommsdoc,
      vnddotpawaafile,
      vnddotpcos,
      vnddotpgdotformat,
      vnddotpgdotosasli,
      vnddotpiaccessdotapplicationLicence,
      vnddotpicsel,
      vnddotpmidotwidget,
      vnddotpmtiles,
      vnddotpocdotgroupAdvertisementplusxml,
      vnddotpocketlearn,
      vnddotpowerbuilder6,
      vnddotpowerbuilder6S,
      vnddotpowerbuilder7,
      vnddotpowerbuilder7S,
      vnddotpowerbuilder75,
      vnddotpowerbuilder75S,
      vnddotppdotsystemverifyplusxml,
      vnddotpreminet,
      vnddotpreviewsystemsdotbox,
      vnddotprocreatedotbrush,
      vnddotprocreatedotbrushset,
      vnddotprocreatedotdream,
      vnddotprojectGraph,
      vnddotproteusdotmagazine,
      vnddotpsfs,
      vnddotptdotmundusmundi,
      vnddotpublishareDeltaTree,
      vnddotpvidotptid1,
      vnddotpwgMultiplexed,
      vnddotpwgXhtmlPrintplusxml,
      vnddotpyonplusjson,
      vnddotqualcommdotbrewAppRes,
      vnddotquarantainenet,
      vnddotquarkdotquarkxpress,
      vnddotquobjectQuoxdocument,
      vnddotr74ndotsandboxelsplusjson,
      vnddotradisysdotmomlplusxml,
      vnddotradisysdotmsmlplusxml,
      vnddotradisysdotmsmlAuditplusxml,
      vnddotradisysdotmsmlAuditConfplusxml,
      vnddotradisysdotmsmlAuditConnplusxml,
      vnddotradisysdotmsmlAuditDialogplusxml,
      vnddotradisysdotmsmlAuditStreamplusxml,
      vnddotradisysdotmsmlConfplusxml,
      vnddotradisysdotmsmlDialogplusxml,
      vnddotradisysdotmsmlDialogBaseplusxml,
      vnddotradisysdotmsmlDialogFaxDetectplusxml,
      vnddotradisysdotmsmlDialogFaxSendrecvplusxml,
      vnddotradisysdotmsmlDialogGroupplusxml,
      vnddotradisysdotmsmlDialogSpeechplusxml,
      vnddotradisysdotmsmlDialogTransformplusxml,
      vnddotrainstordotdata,
      vnddotrapid,
      vnddotrar,
      vnddotrealvncdotbed,
      vnddotrecordaredotmusicxml,
      vnddotrecordaredotmusicxmlplusxml,
      vnddotrelpipe,
      vnddotrenlearndotrlprint,
      vnddotresilientdotlogic,
      vnddotrestfulplusjson,
      vnddotrigdotcryptonote,
      vnddotrimdotcod,
      vnddotrnRealmedia,
      vnddotrnRealmediaVbr,
      vnddotroute66dotlink66plusxml,
      vnddotrs274x,
      vnddotruckusdotdownload,
      vnddots3sms,
      vnddotsailingtrackerdottrack,
      vnddotsar,
      vnddotsbmdotcid,
      vnddotsbmdotmid2,
      vnddotscribus,
      vnddotsealeddot3df,
      vnddotsealeddotcsf,
      vnddotsealeddotdoc,
      vnddotsealeddoteml,
      vnddotsealeddotmht,
      vnddotsealeddotnet,
      vnddotsealeddotppt,
      vnddotsealeddottiff,
      vnddotsealeddotxls,
      vnddotsealedmediadotsoftsealdothtml,
      vnddotsealedmediadotsoftsealdotpdf,
      vnddotseemail,
      vnddotseisplusjson,
      vnddotsema,
      vnddotsemd,
      vnddotsemf,
      vnddotshadeSaveFile,
      vnddotshanadotinformeddotformdata,
      vnddotshanadotinformeddotformtemplate,
      vnddotshanadotinformeddotinterchange,
      vnddotshanadotinformeddotpackage,
      vnddotshootproofplusjson,
      vnddotshopkickplusjson,
      vnddotshp,
      vnddotshx,
      vnddotsigrokdotsession,
      vnddotsimtechMindmapper,
      vnddotsirenplusjson,
      vnddotsirtxdotvmv0,
      vnddotsketchometry,
      vnddotsmaf,
      vnddotsmartdotnotebook,
      vnddotsmartdotteacher,
      vnddotsmintiodotportalsdotarchive,
      vnddotsnesdevPageTable,
      vnddotsoftware602dotfillerdotformplusxml,
      vnddotsoftware602dotfillerdotformXmlZip,
      vnddotsolentdotsdkmplusxml,
      vnddotspotfiredotdxp,
      vnddotspotfiredotsfs,
      vnddotsqlite3,
      vnddotsssCod,
      vnddotsssDtf,
      vnddotsssNtf,
      vnddotstardivisiondotcalc,
      vnddotstardivisiondotdraw,
      vnddotstardivisiondotimpress,
      vnddotstardivisiondotmath,
      vnddotstardivisiondotwriter,
      vnddotstardivisiondotwriterGlobal,
      vnddotstepmaniadotpackage,
      vnddotstepmaniadotstepchart,
      vnddotstreetStream,
      vnddotsundotwadlplusxml,
      vnddotsundotxmldotcalc,
      vnddotsundotxmldotcalcdottemplate,
      vnddotsundotxmldotdraw,
      vnddotsundotxmldotdrawdottemplate,
      vnddotsundotxmldotimpress,
      vnddotsundotxmldotimpressdottemplate,
      vnddotsundotxmldotmath,
      vnddotsundotxmldotwriter,
      vnddotsundotxmldotwriterdotglobal,
      vnddotsundotxmldotwriterdottemplate,
      vnddotsuperfiledotsuper,
      vnddotsusCalendar,
      vnddotsvd,
      vnddotswiftviewIcs,
      vnddotsybyldotmol2,
      vnddotsycleplusxml,
      vnddotsyftplusjson,
      vnddotsymbiandotinstall,
      vnddotsyncmlplusxml,
      vnddotsyncmldotdmpluswbxml,
      vnddotsyncmldotdmplusxml,
      vnddotsyncmldotdmdotnotification,
      vnddotsyncmldotdmddfpluswbxml,
      vnddotsyncmldotdmddfplusxml,
      vnddotsyncmldotdmtndspluswbxml,
      vnddotsyncmldotdmtndsplusxml,
      vnddotsyncmldotdsdotnotification,
      vnddottableschemaplusjson,
      vnddottaodotintentModuleArchive,
      vnddottcpdumpdotpcap,
      vnddotthinkCelldotppttcplusjson,
      vnddottmddotmediaflexdotapiplusxml,
      vnddottml,
      vnddottmobileLivetv,
      vnddottridotonesource,
      vnddottriddottpt,
      vnddottriscapedotmxs,
      vnddottrueapp,
      vnddottruedoc,
      vnddotubisoftdotwebplayer,
      vnddotufdl,
      vnddotuicdotdosipasdotv1,
      vnddotuicdotdosipasdotv2,
      vnddotuicdotosdmplusjson,
      vnddotuicdottlbFcb,
      vnddotuiqdottheme,
      vnddotumajin,
      vnddotunity,
      vnddotuomlplusxml,
      vnddotuplanetdotalert,
      vnddotuplanetdotalertWbxml,
      vnddotuplanetdotbearerChoice,
      vnddotuplanetdotbearerChoiceWbxml,
      vnddotuplanetdotcacheop,
      vnddotuplanetdotcacheopWbxml,
      vnddotuplanetdotchannel,
      vnddotuplanetdotchannelWbxml,
      vnddotuplanetdotlist,
      vnddotuplanetdotlistWbxml,
      vnddotuplanetdotlistcmd,
      vnddotuplanetdotlistcmdWbxml,
      vnddotuplanetdotsignal,
      vnddoturiMap,
      vnddotvalvedotsourcedotmaterial,
      vnddotvcx,
      vnddotvdStudy,
      vnddotvectorworks,
      vnddotvelplusjson,
      vnddotveraisondottsmReportpluscbor,
      vnddotveraisondottsmReportplusjson,
      vnddotverifierAttestationplusjwt,
      vnddotverimatrixdotvcas,
      vnddotveritonedotaionplusjson,
      vnddotveryantdotthin,
      vnddotvesdotencrypted,
      vnddotvidsoftdotvidconference,
      vnddotvisio,
      vnddotvisionary,
      vnddotvividencedotscriptfile,
      vnddotvocalshaperdotvsp4,
      vnddotvsf,
      vnddotvuq,
      vnddotwantverse,
      vnddotwapdotsic,
      vnddotwapdotslc,
      vnddotwapdotwbxml,
      vnddotwapdotwmlc,
      vnddotwapdotwmlscriptc,
      vnddotwasmflowdotwafl,
      vnddotwebturbo,
      vnddotwfadotdpp,
      vnddotwfadotp2p,
      vnddotwfadotwsc,
      vnddotwindowsdotdevicepairing,
      vnddotwmap,
      vnddotwmc,
      vnddotwmfdotbootstrap,
      vnddotwolframdotmathematica,
      vnddotwolframdotmathematicadotpackage,
      vnddotwolframdotplayer,
      vnddotwordlift,
      vnddotwordperfect,
      vnddotwqd,
      vnddotwrqHp3000Labelled,
      vnddotwtdotstf,
      vnddotwvdotcsppluswbxml,
      vnddotwvdotcspplusxml,
      vnddotwvdotsspplusxml,
      vnddotxacmlplusjson,
      vnddotxara,
      vnddotxarindotcpj,
      vnddotxcdn,
      vnddotxecretsEncrypted,
      vnddotxfdl,
      vnddotxfdldotwebform,
      vnddotxmiplusxml,
      vnddotxmpiedotcpkg,
      vnddotxmpiedotdpkg,
      vnddotxmpiedotplan,
      vnddotxmpiedotppkg,
      vnddotxmpiedotxlim,
      vnddotyamahadothvDic,
      vnddotyamahadothvScript,
      vnddotyamahadothvVoice,
      vnddotyamahadotopenscoreformat,
      vnddotyamahadotopenscoreformatdotosfpvgplusxml,
      vnddotyamahadotremoteSetup,
      vnddotyamahadotsmafAudio,
      vnddotyamahadotsmafPhrase,
      vnddotyamahadotthroughNgn,
      vnddotyamahadottunnelUdpencap,
      vnddotyaoweme,
      vnddotyellowriverCustomMenu,
      vnddotzohoPresentationdotshow,
      vnddotzul,
      vnddotzzazzdotdeckplusxml,
      voicexmlplusxml,
      voucherCmsplusjson,
      voucherJwsplusjson,
      vp,
      vppluscose,
      vpplusjwt,
      vpplussdJwt,
      vqRtcpxr,
      wasm,
      watcherinfoplusxml,
      webpushOptionsplusjson,
      whoisppQuery,
      whoisppResponse,
      widget,
      winhlp,
      wita,
      wordperfect5dot1,
      wsdlplusxml,
      wspolicyplusxml,
      x7zCompressed,
      xAbiword,
      xAceCompressed,
      xAmf,
      xAppleDiskimage,
      xArj,
      xAuthorwareBin,
      xAuthorwareMap,
      xAuthorwareSeg,
      xBcpio,
      xBdoc,
      xBittorrent,
      xBlender,
      xBlorb,
      xBzip,
      xBzip2,
      xCbr,
      xCdlink,
      xCfsCompressed,
      xChat,
      xChessPgn,
      xChromeExtension,
      xCocoa,
      xCompress,
      xCompressed,
      xConference,
      xCpio,
      xCsh,
      xDeb,
      xDebianPackage,
      xDgcCompressed,
      xDirector,
      xDoom,
      xDtbncxplusxml,
      xDtbookplusxml,
      xDtbresourceplusxml,
      xDvi,
      xEnvoy,
      xEva,
      xFontBdf,
      xFontDos,
      xFontFramemaker,
      xFontGhostscript,
      xFontLibgrx,
      xFontLinuxPsf,
      xFontPcf,
      xFontSnf,
      xFontSpeedo,
      xFontSunosNews,
      xFontType1,
      xFontVfont,
      xFreearc,
      xFuturesplash,
      xGcaCompressed,
      xGlulx,
      xGnumeric,
      xGrampsXml,
      xGtar,
      xGzip,
      xHdf,
      xHttpdPhp,
      xInstallInstructions,
      xIpynbplusjson,
      xIso9660Image,
      xIworkKeynoteSffkey,
      xIworkNumbersSffnumbers,
      xIworkPagesSffpages,
      xJavaArchiveDiff,
      xJavaJnlpFile,
      xJavascript,
      xKeepass2,
      xLatex,
      xLuaBytecode,
      xLzhCompressed,
      xMakeself,
      xMie,
      xMobipocketEbook,
      xMpegurl,
      xMsApplication,
      xMsShortcut,
      xMsWmd,
      xMsWmz,
      xMsXbap,
      xMsaccess,
      xMsbinder,
      xMscardfile,
      xMsclip,
      xMsdosProgram,
      xMsdownload,
      xMsmediaview,
      xMsmetafile,
      xMsmoney,
      xMspublisher,
      xMsschedule,
      xMsterminal,
      xMswrite,
      xNetcdf,
      xNsProxyAutoconfig,
      xNzb,
      xPerl,
      xPilot,
      xPkcs12,
      xPkcs7Certificates,
      xPkcs7Certreqresp,
      xPkiMessage,
      xRarCompressed,
      xRedhatPackageManager,
      xResearchInfoSystems,
      xSea,
      xSh,
      xShar,
      xShockwaveFlash,
      xSilverlightApp,
      xSql,
      xStuffit,
      xStuffitx,
      xSubrip,
      xSv4cpio,
      xSv4crc,
      xT3vmImage,
      xTads,
      xTar,
      xTcl,
      xTex,
      xTexTfm,
      xTexinfo,
      xTgif,
      xUstar,
      xVirtualboxHdd,
      xVirtualboxOva,
      xVirtualboxOvf,
      xVirtualboxVbox,
      xVirtualboxVboxExtpack,
      xVirtualboxVdi,
      xVirtualboxVhd,
      xVirtualboxVmdk,
      xWaisSource,
      xWebAppManifestplusjson,
      xWwwFormUrlencoded,
      xX509CaCert,
      xX509CaRaCert,
      xX509NextCaCert,
      xXfig,
      xXliffplusxml,
      xXpinstall,
      xXz,
      xZipCompressed,
      xZmachine,
      x400Bp,
      xacmlplusxml,
      xamlplusxml,
      xcapAttplusxml,
      xcapCapsplusxml,
      xcapDiffplusxml,
      xcapElplusxml,
      xcapErrorplusxml,
      xcapNsplusxml,
      xconConferenceInfoplusxml,
      xconConferenceInfoDiffplusxml,
      xencplusxml,
      xfdf,
      xhtmlplusxml,
      xhtmlVoiceplusxml,
      xliffplusxml,
      xml,
      xmlDtd,
      xmlExternalParsedEntity,
      xmlPatchplusxml,
      xmppplusxml,
      xopplusxml,
      xprocplusxml,
      xsltplusxml,
      xspfplusxml,
      xvplusxml,
      yaml,
      yang,
      yangDatapluscbor,
      yangDataplusjson,
      yangDataplusxml,
      yangPatchplusjson,
      yangPatchplusxml,
      yangSidplusjson,
      yinplusxml,
      zip,
      zipplusdotlottie,
      zlib,
      zstd
    )
  }

  object audio {
    lazy val _1dInterleavedParityfec: MediaType =
      MediaType("audio", "1d-interleaved-parityfec", compressible = false, binary = true)

    lazy val _32kadpcm: MediaType =
      MediaType("audio", "32kadpcm", compressible = false, binary = true)

    lazy val _3gpp: MediaType =
      MediaType("audio", "3gpp", compressible = false, binary = true, fileExtensions = List("3gpp"))

    lazy val _3gpp2: MediaType =
      MediaType("audio", "3gpp2", compressible = false, binary = true)

    lazy val aac: MediaType =
      MediaType("audio", "aac", compressible = false, binary = true, fileExtensions = List("adts", "aac"))

    lazy val ac3: MediaType =
      MediaType("audio", "ac3", compressible = false, binary = true)

    lazy val adpcm: MediaType =
      MediaType("audio", "adpcm", compressible = false, binary = true, fileExtensions = List("adp"))

    lazy val amr: MediaType =
      MediaType("audio", "amr", compressible = false, binary = true, fileExtensions = List("amr"))

    lazy val amrWb: MediaType =
      MediaType("audio", "amr-wb", compressible = false, binary = true)

    lazy val amrWbplus: MediaType =
      MediaType("audio", "amr-wb+", compressible = false, binary = true)

    lazy val aptx: MediaType =
      MediaType("audio", "aptx", compressible = false, binary = true)

    lazy val asc: MediaType =
      MediaType("audio", "asc", compressible = false, binary = true)

    lazy val atracAdvancedLossless: MediaType =
      MediaType("audio", "atrac-advanced-lossless", compressible = false, binary = true)

    lazy val atracX: MediaType =
      MediaType("audio", "atrac-x", compressible = false, binary = true)

    lazy val atrac3: MediaType =
      MediaType("audio", "atrac3", compressible = false, binary = true)

    lazy val basic: MediaType =
      MediaType("audio", "basic", compressible = false, binary = true, fileExtensions = List("au", "snd"))

    lazy val bv16: MediaType =
      MediaType("audio", "bv16", compressible = false, binary = true)

    lazy val bv32: MediaType =
      MediaType("audio", "bv32", compressible = false, binary = true)

    lazy val clearmode: MediaType =
      MediaType("audio", "clearmode", compressible = false, binary = true)

    lazy val cn: MediaType =
      MediaType("audio", "cn", compressible = false, binary = true)

    lazy val dat12: MediaType =
      MediaType("audio", "dat12", compressible = false, binary = true)

    lazy val dls: MediaType =
      MediaType("audio", "dls", compressible = false, binary = true)

    lazy val dsrEs201108: MediaType =
      MediaType("audio", "dsr-es201108", compressible = false, binary = true)

    lazy val dsrEs202050: MediaType =
      MediaType("audio", "dsr-es202050", compressible = false, binary = true)

    lazy val dsrEs202211: MediaType =
      MediaType("audio", "dsr-es202211", compressible = false, binary = true)

    lazy val dsrEs202212: MediaType =
      MediaType("audio", "dsr-es202212", compressible = false, binary = true)

    lazy val dv: MediaType =
      MediaType("audio", "dv", compressible = false, binary = true)

    lazy val dvi4: MediaType =
      MediaType("audio", "dvi4", compressible = false, binary = true)

    lazy val eac3: MediaType =
      MediaType("audio", "eac3", compressible = false, binary = true)

    lazy val encaprtp: MediaType =
      MediaType("audio", "encaprtp", compressible = false, binary = true)

    lazy val evrc: MediaType =
      MediaType("audio", "evrc", compressible = false, binary = true)

    lazy val evrcQcp: MediaType =
      MediaType("audio", "evrc-qcp", compressible = false, binary = true)

    lazy val evrc0: MediaType =
      MediaType("audio", "evrc0", compressible = false, binary = true)

    lazy val evrc1: MediaType =
      MediaType("audio", "evrc1", compressible = false, binary = true)

    lazy val evrcb: MediaType =
      MediaType("audio", "evrcb", compressible = false, binary = true)

    lazy val evrcb0: MediaType =
      MediaType("audio", "evrcb0", compressible = false, binary = true)

    lazy val evrcb1: MediaType =
      MediaType("audio", "evrcb1", compressible = false, binary = true)

    lazy val evrcnw: MediaType =
      MediaType("audio", "evrcnw", compressible = false, binary = true)

    lazy val evrcnw0: MediaType =
      MediaType("audio", "evrcnw0", compressible = false, binary = true)

    lazy val evrcnw1: MediaType =
      MediaType("audio", "evrcnw1", compressible = false, binary = true)

    lazy val evrcwb: MediaType =
      MediaType("audio", "evrcwb", compressible = false, binary = true)

    lazy val evrcwb0: MediaType =
      MediaType("audio", "evrcwb0", compressible = false, binary = true)

    lazy val evrcwb1: MediaType =
      MediaType("audio", "evrcwb1", compressible = false, binary = true)

    lazy val evs: MediaType =
      MediaType("audio", "evs", compressible = false, binary = true)

    lazy val flac: MediaType =
      MediaType("audio", "flac", compressible = false, binary = true)

    lazy val flexfec: MediaType =
      MediaType("audio", "flexfec", compressible = false, binary = true)

    lazy val fwdred: MediaType =
      MediaType("audio", "fwdred", compressible = false, binary = true)

    lazy val g7110: MediaType =
      MediaType("audio", "g711-0", compressible = false, binary = true)

    lazy val g719: MediaType =
      MediaType("audio", "g719", compressible = false, binary = true)

    lazy val g722: MediaType =
      MediaType("audio", "g722", compressible = false, binary = true)

    lazy val g7221: MediaType =
      MediaType("audio", "g7221", compressible = false, binary = true)

    lazy val g723: MediaType =
      MediaType("audio", "g723", compressible = false, binary = true)

    lazy val g72616: MediaType =
      MediaType("audio", "g726-16", compressible = false, binary = true)

    lazy val g72624: MediaType =
      MediaType("audio", "g726-24", compressible = false, binary = true)

    lazy val g72632: MediaType =
      MediaType("audio", "g726-32", compressible = false, binary = true)

    lazy val g72640: MediaType =
      MediaType("audio", "g726-40", compressible = false, binary = true)

    lazy val g728: MediaType =
      MediaType("audio", "g728", compressible = false, binary = true)

    lazy val g729: MediaType =
      MediaType("audio", "g729", compressible = false, binary = true)

    lazy val g7291: MediaType =
      MediaType("audio", "g7291", compressible = false, binary = true)

    lazy val g729d: MediaType =
      MediaType("audio", "g729d", compressible = false, binary = true)

    lazy val g729e: MediaType =
      MediaType("audio", "g729e", compressible = false, binary = true)

    lazy val gsm: MediaType =
      MediaType("audio", "gsm", compressible = false, binary = true)

    lazy val gsmEfr: MediaType =
      MediaType("audio", "gsm-efr", compressible = false, binary = true)

    lazy val gsmHr08: MediaType =
      MediaType("audio", "gsm-hr-08", compressible = false, binary = true)

    lazy val ilbc: MediaType =
      MediaType("audio", "ilbc", compressible = false, binary = true)

    lazy val ipMrV2dot5: MediaType =
      MediaType("audio", "ip-mr_v2.5", compressible = false, binary = true)

    lazy val isac: MediaType =
      MediaType("audio", "isac", compressible = false, binary = true)

    lazy val l16: MediaType =
      MediaType("audio", "l16", compressible = false, binary = true)

    lazy val l20: MediaType =
      MediaType("audio", "l20", compressible = false, binary = true)

    lazy val l24: MediaType =
      MediaType("audio", "l24", compressible = false, binary = true)

    lazy val l8: MediaType =
      MediaType("audio", "l8", compressible = false, binary = true)

    lazy val lpc: MediaType =
      MediaType("audio", "lpc", compressible = false, binary = true)

    lazy val matroska: MediaType =
      MediaType("audio", "matroska", compressible = false, binary = true, fileExtensions = List("mka"))

    lazy val melp: MediaType =
      MediaType("audio", "melp", compressible = false, binary = true)

    lazy val melp1200: MediaType =
      MediaType("audio", "melp1200", compressible = false, binary = true)

    lazy val melp2400: MediaType =
      MediaType("audio", "melp2400", compressible = false, binary = true)

    lazy val melp600: MediaType =
      MediaType("audio", "melp600", compressible = false, binary = true)

    lazy val mhas: MediaType =
      MediaType("audio", "mhas", compressible = false, binary = true)

    lazy val midi: MediaType =
      MediaType(
        "audio",
        "midi",
        compressible = false,
        binary = true,
        fileExtensions = List("mid", "midi", "kar", "rmi")
      )

    lazy val midiClip: MediaType =
      MediaType("audio", "midi-clip", compressible = false, binary = true)

    lazy val mobileXmf: MediaType =
      MediaType("audio", "mobile-xmf", compressible = false, binary = true, fileExtensions = List("mxmf"))

    lazy val mp3: MediaType =
      MediaType("audio", "mp3", compressible = false, binary = true, fileExtensions = List("mp3"))

    lazy val mp4: MediaType =
      MediaType("audio", "mp4", compressible = false, binary = true, fileExtensions = List("m4a", "mp4a", "m4b"))

    lazy val mp4aLatm: MediaType =
      MediaType("audio", "mp4a-latm", compressible = false, binary = true)

    lazy val mpa: MediaType =
      MediaType("audio", "mpa", compressible = false, binary = true)

    lazy val mpaRobust: MediaType =
      MediaType("audio", "mpa-robust", compressible = false, binary = true)

    lazy val mpeg: MediaType =
      MediaType(
        "audio",
        "mpeg",
        compressible = false,
        binary = true,
        fileExtensions = List("mpga", "mp2", "mp2a", "mp3", "m2a", "m3a")
      )

    lazy val mpeg4Generic: MediaType =
      MediaType("audio", "mpeg4-generic", compressible = false, binary = true)

    lazy val musepack: MediaType =
      MediaType("audio", "musepack", compressible = false, binary = true)

    lazy val ogg: MediaType =
      MediaType("audio", "ogg", compressible = false, binary = true, fileExtensions = List("oga", "ogg", "spx", "opus"))

    lazy val opus: MediaType =
      MediaType("audio", "opus", compressible = false, binary = true)

    lazy val parityfec: MediaType =
      MediaType("audio", "parityfec", compressible = false, binary = true)

    lazy val pcma: MediaType =
      MediaType("audio", "pcma", compressible = false, binary = true)

    lazy val pcmaWb: MediaType =
      MediaType("audio", "pcma-wb", compressible = false, binary = true)

    lazy val pcmu: MediaType =
      MediaType("audio", "pcmu", compressible = false, binary = true)

    lazy val pcmuWb: MediaType =
      MediaType("audio", "pcmu-wb", compressible = false, binary = true)

    lazy val prsdotsid: MediaType =
      MediaType("audio", "prs.sid", compressible = false, binary = true)

    lazy val qcelp: MediaType =
      MediaType("audio", "qcelp", compressible = false, binary = true)

    lazy val raptorfec: MediaType =
      MediaType("audio", "raptorfec", compressible = false, binary = true)

    lazy val red: MediaType =
      MediaType("audio", "red", compressible = false, binary = true)

    lazy val rtpEncAescm128: MediaType =
      MediaType("audio", "rtp-enc-aescm128", compressible = false, binary = true)

    lazy val rtpMidi: MediaType =
      MediaType("audio", "rtp-midi", compressible = false, binary = true)

    lazy val rtploopback: MediaType =
      MediaType("audio", "rtploopback", compressible = false, binary = true)

    lazy val rtx: MediaType =
      MediaType("audio", "rtx", compressible = false, binary = true)

    lazy val s3m: MediaType =
      MediaType("audio", "s3m", compressible = false, binary = true, fileExtensions = List("s3m"))

    lazy val scip: MediaType =
      MediaType("audio", "scip", compressible = false, binary = true)

    lazy val silk: MediaType =
      MediaType("audio", "silk", compressible = false, binary = true, fileExtensions = List("sil"))

    lazy val smv: MediaType =
      MediaType("audio", "smv", compressible = false, binary = true)

    lazy val smvQcp: MediaType =
      MediaType("audio", "smv-qcp", compressible = false, binary = true)

    lazy val smv0: MediaType =
      MediaType("audio", "smv0", compressible = false, binary = true)

    lazy val sofa: MediaType =
      MediaType("audio", "sofa", compressible = false, binary = true)

    lazy val spMidi: MediaType =
      MediaType("audio", "sp-midi", compressible = false, binary = true)

    lazy val speex: MediaType =
      MediaType("audio", "speex", compressible = false, binary = true)

    lazy val t140c: MediaType =
      MediaType("audio", "t140c", compressible = false, binary = true)

    lazy val t38: MediaType =
      MediaType("audio", "t38", compressible = false, binary = true)

    lazy val telephoneEvent: MediaType =
      MediaType("audio", "telephone-event", compressible = false, binary = true)

    lazy val tetraAcelp: MediaType =
      MediaType("audio", "tetra_acelp", compressible = false, binary = true)

    lazy val tetraAcelpBb: MediaType =
      MediaType("audio", "tetra_acelp_bb", compressible = false, binary = true)

    lazy val tone: MediaType =
      MediaType("audio", "tone", compressible = false, binary = true)

    lazy val tsvcis: MediaType =
      MediaType("audio", "tsvcis", compressible = false, binary = true)

    lazy val uemclip: MediaType =
      MediaType("audio", "uemclip", compressible = false, binary = true)

    lazy val ulpfec: MediaType =
      MediaType("audio", "ulpfec", compressible = false, binary = true)

    lazy val usac: MediaType =
      MediaType("audio", "usac", compressible = false, binary = true)

    lazy val vdvi: MediaType =
      MediaType("audio", "vdvi", compressible = false, binary = true)

    lazy val vmrWb: MediaType =
      MediaType("audio", "vmr-wb", compressible = false, binary = true)

    lazy val vnddot3gppdotiufp: MediaType =
      MediaType("audio", "vnd.3gpp.iufp", compressible = false, binary = true)

    lazy val vnddot4sb: MediaType =
      MediaType("audio", "vnd.4sb", compressible = false, binary = true)

    lazy val vnddotaudiokoz: MediaType =
      MediaType("audio", "vnd.audiokoz", compressible = false, binary = true)

    lazy val vnddotblockfactdotfacta: MediaType =
      MediaType("audio", "vnd.blockfact.facta", compressible = false, binary = true)

    lazy val vnddotcelp: MediaType =
      MediaType("audio", "vnd.celp", compressible = false, binary = true)

    lazy val vnddotciscodotnse: MediaType =
      MediaType("audio", "vnd.cisco.nse", compressible = false, binary = true)

    lazy val vnddotcmlesdotradioEvents: MediaType =
      MediaType("audio", "vnd.cmles.radio-events", compressible = false, binary = true)

    lazy val vnddotcnsdotanp1: MediaType =
      MediaType("audio", "vnd.cns.anp1", compressible = false, binary = true)

    lazy val vnddotcnsdotinf1: MediaType =
      MediaType("audio", "vnd.cns.inf1", compressible = false, binary = true)

    lazy val vnddotdecedotaudio: MediaType =
      MediaType("audio", "vnd.dece.audio", compressible = false, binary = true, fileExtensions = List("uva", "uvva"))

    lazy val vnddotdigitalWinds: MediaType =
      MediaType("audio", "vnd.digital-winds", compressible = false, binary = true, fileExtensions = List("eol"))

    lazy val vnddotdlnadotadts: MediaType =
      MediaType("audio", "vnd.dlna.adts", compressible = false, binary = true)

    lazy val vnddotdolbydotheaacdot1: MediaType =
      MediaType("audio", "vnd.dolby.heaac.1", compressible = false, binary = true)

    lazy val vnddotdolbydotheaacdot2: MediaType =
      MediaType("audio", "vnd.dolby.heaac.2", compressible = false, binary = true)

    lazy val vnddotdolbydotmlp: MediaType =
      MediaType("audio", "vnd.dolby.mlp", compressible = false, binary = true)

    lazy val vnddotdolbydotmps: MediaType =
      MediaType("audio", "vnd.dolby.mps", compressible = false, binary = true)

    lazy val vnddotdolbydotpl2: MediaType =
      MediaType("audio", "vnd.dolby.pl2", compressible = false, binary = true)

    lazy val vnddotdolbydotpl2x: MediaType =
      MediaType("audio", "vnd.dolby.pl2x", compressible = false, binary = true)

    lazy val vnddotdolbydotpl2z: MediaType =
      MediaType("audio", "vnd.dolby.pl2z", compressible = false, binary = true)

    lazy val vnddotdolbydotpulsedot1: MediaType =
      MediaType("audio", "vnd.dolby.pulse.1", compressible = false, binary = true)

    lazy val vnddotdra: MediaType =
      MediaType("audio", "vnd.dra", compressible = false, binary = true, fileExtensions = List("dra"))

    lazy val vnddotdts: MediaType =
      MediaType("audio", "vnd.dts", compressible = false, binary = true, fileExtensions = List("dts"))

    lazy val vnddotdtsdothd: MediaType =
      MediaType("audio", "vnd.dts.hd", compressible = false, binary = true, fileExtensions = List("dtshd"))

    lazy val vnddotdtsdotuhd: MediaType =
      MediaType("audio", "vnd.dts.uhd", compressible = false, binary = true)

    lazy val vnddotdvbdotfile: MediaType =
      MediaType("audio", "vnd.dvb.file", compressible = false, binary = true)

    lazy val vnddoteveraddotplj: MediaType =
      MediaType("audio", "vnd.everad.plj", compressible = false, binary = true)

    lazy val vnddothnsdotaudio: MediaType =
      MediaType("audio", "vnd.hns.audio", compressible = false, binary = true)

    lazy val vnddotlucentdotvoice: MediaType =
      MediaType("audio", "vnd.lucent.voice", compressible = false, binary = true, fileExtensions = List("lvp"))

    lazy val vnddotmsPlayreadydotmediadotpya: MediaType =
      MediaType(
        "audio",
        "vnd.ms-playready.media.pya",
        compressible = false,
        binary = true,
        fileExtensions = List("pya")
      )

    lazy val vnddotnokiadotmobileXmf: MediaType =
      MediaType("audio", "vnd.nokia.mobile-xmf", compressible = false, binary = true)

    lazy val vnddotnorteldotvbk: MediaType =
      MediaType("audio", "vnd.nortel.vbk", compressible = false, binary = true)

    lazy val vnddotnueradotecelp4800: MediaType =
      MediaType("audio", "vnd.nuera.ecelp4800", compressible = false, binary = true, fileExtensions = List("ecelp4800"))

    lazy val vnddotnueradotecelp7470: MediaType =
      MediaType("audio", "vnd.nuera.ecelp7470", compressible = false, binary = true, fileExtensions = List("ecelp7470"))

    lazy val vnddotnueradotecelp9600: MediaType =
      MediaType("audio", "vnd.nuera.ecelp9600", compressible = false, binary = true, fileExtensions = List("ecelp9600"))

    lazy val vnddotocteldotsbc: MediaType =
      MediaType("audio", "vnd.octel.sbc", compressible = false, binary = true)

    lazy val vnddotpresonusdotmultitrack: MediaType =
      MediaType("audio", "vnd.presonus.multitrack", compressible = false, binary = true)

    lazy val vnddotqcelp: MediaType =
      MediaType("audio", "vnd.qcelp", compressible = false, binary = true)

    lazy val vnddotrhetorexdot32kadpcm: MediaType =
      MediaType("audio", "vnd.rhetorex.32kadpcm", compressible = false, binary = true)

    lazy val vnddotrip: MediaType =
      MediaType("audio", "vnd.rip", compressible = false, binary = true, fileExtensions = List("rip"))

    lazy val vnddotrnRealaudio: MediaType =
      MediaType("audio", "vnd.rn-realaudio", compressible = false, binary = true)

    lazy val vnddotsealedmediadotsoftsealdotmpeg: MediaType =
      MediaType("audio", "vnd.sealedmedia.softseal.mpeg", compressible = false, binary = true)

    lazy val vnddotvmxdotcvsd: MediaType =
      MediaType("audio", "vnd.vmx.cvsd", compressible = false, binary = true)

    lazy val vnddotwave: MediaType =
      MediaType("audio", "vnd.wave", compressible = false, binary = true)

    lazy val vorbis: MediaType =
      MediaType("audio", "vorbis", compressible = false, binary = true)

    lazy val vorbisConfig: MediaType =
      MediaType("audio", "vorbis-config", compressible = false, binary = true)

    lazy val wav: MediaType =
      MediaType("audio", "wav", compressible = false, binary = true, fileExtensions = List("wav"))

    lazy val wave: MediaType =
      MediaType("audio", "wave", compressible = false, binary = true, fileExtensions = List("wav"))

    lazy val webm: MediaType =
      MediaType("audio", "webm", compressible = false, binary = true, fileExtensions = List("weba"))

    lazy val xAac: MediaType =
      MediaType("audio", "x-aac", compressible = false, binary = true, fileExtensions = List("aac"))

    lazy val xAiff: MediaType =
      MediaType("audio", "x-aiff", compressible = false, binary = true, fileExtensions = List("aif", "aiff", "aifc"))

    lazy val xCaf: MediaType =
      MediaType("audio", "x-caf", compressible = false, binary = true, fileExtensions = List("caf"))

    lazy val xFlac: MediaType =
      MediaType("audio", "x-flac", compressible = false, binary = true, fileExtensions = List("flac"))

    lazy val xM4a: MediaType =
      MediaType("audio", "x-m4a", compressible = false, binary = true, fileExtensions = List("m4a"))

    lazy val xMatroska: MediaType =
      MediaType("audio", "x-matroska", compressible = false, binary = true, fileExtensions = List("mka"))

    lazy val xMpegurl: MediaType =
      MediaType("audio", "x-mpegurl", compressible = false, binary = true, fileExtensions = List("m3u"))

    lazy val xMsWax: MediaType =
      MediaType("audio", "x-ms-wax", compressible = false, binary = true, fileExtensions = List("wax"))

    lazy val xMsWma: MediaType =
      MediaType("audio", "x-ms-wma", compressible = false, binary = true, fileExtensions = List("wma"))

    lazy val xPnRealaudio: MediaType =
      MediaType("audio", "x-pn-realaudio", compressible = false, binary = true, fileExtensions = List("ram", "ra"))

    lazy val xPnRealaudioPlugin: MediaType =
      MediaType("audio", "x-pn-realaudio-plugin", compressible = false, binary = true, fileExtensions = List("rmp"))

    lazy val xRealaudio: MediaType =
      MediaType("audio", "x-realaudio", compressible = false, binary = true, fileExtensions = List("ra"))

    lazy val xTta: MediaType =
      MediaType("audio", "x-tta", compressible = false, binary = true)

    lazy val xWav: MediaType =
      MediaType("audio", "x-wav", compressible = false, binary = true, fileExtensions = List("wav"))

    lazy val xm: MediaType =
      MediaType("audio", "xm", compressible = false, binary = true, fileExtensions = List("xm"))

    def all: List[MediaType] = List(
      _1dInterleavedParityfec,
      _32kadpcm,
      _3gpp,
      _3gpp2,
      aac,
      ac3,
      adpcm,
      amr,
      amrWb,
      amrWbplus,
      aptx,
      asc,
      atracAdvancedLossless,
      atracX,
      atrac3,
      basic,
      bv16,
      bv32,
      clearmode,
      cn,
      dat12,
      dls,
      dsrEs201108,
      dsrEs202050,
      dsrEs202211,
      dsrEs202212,
      dv,
      dvi4,
      eac3,
      encaprtp,
      evrc,
      evrcQcp,
      evrc0,
      evrc1,
      evrcb,
      evrcb0,
      evrcb1,
      evrcnw,
      evrcnw0,
      evrcnw1,
      evrcwb,
      evrcwb0,
      evrcwb1,
      evs,
      flac,
      flexfec,
      fwdred,
      g7110,
      g719,
      g722,
      g7221,
      g723,
      g72616,
      g72624,
      g72632,
      g72640,
      g728,
      g729,
      g7291,
      g729d,
      g729e,
      gsm,
      gsmEfr,
      gsmHr08,
      ilbc,
      ipMrV2dot5,
      isac,
      l16,
      l20,
      l24,
      l8,
      lpc,
      matroska,
      melp,
      melp1200,
      melp2400,
      melp600,
      mhas,
      midi,
      midiClip,
      mobileXmf,
      mp3,
      mp4,
      mp4aLatm,
      mpa,
      mpaRobust,
      mpeg,
      mpeg4Generic,
      musepack,
      ogg,
      opus,
      parityfec,
      pcma,
      pcmaWb,
      pcmu,
      pcmuWb,
      prsdotsid,
      qcelp,
      raptorfec,
      red,
      rtpEncAescm128,
      rtpMidi,
      rtploopback,
      rtx,
      s3m,
      scip,
      silk,
      smv,
      smvQcp,
      smv0,
      sofa,
      spMidi,
      speex,
      t140c,
      t38,
      telephoneEvent,
      tetraAcelp,
      tetraAcelpBb,
      tone,
      tsvcis,
      uemclip,
      ulpfec,
      usac,
      vdvi,
      vmrWb,
      vnddot3gppdotiufp,
      vnddot4sb,
      vnddotaudiokoz,
      vnddotblockfactdotfacta,
      vnddotcelp,
      vnddotciscodotnse,
      vnddotcmlesdotradioEvents,
      vnddotcnsdotanp1,
      vnddotcnsdotinf1,
      vnddotdecedotaudio,
      vnddotdigitalWinds,
      vnddotdlnadotadts,
      vnddotdolbydotheaacdot1,
      vnddotdolbydotheaacdot2,
      vnddotdolbydotmlp,
      vnddotdolbydotmps,
      vnddotdolbydotpl2,
      vnddotdolbydotpl2x,
      vnddotdolbydotpl2z,
      vnddotdolbydotpulsedot1,
      vnddotdra,
      vnddotdts,
      vnddotdtsdothd,
      vnddotdtsdotuhd,
      vnddotdvbdotfile,
      vnddoteveraddotplj,
      vnddothnsdotaudio,
      vnddotlucentdotvoice,
      vnddotmsPlayreadydotmediadotpya,
      vnddotnokiadotmobileXmf,
      vnddotnorteldotvbk,
      vnddotnueradotecelp4800,
      vnddotnueradotecelp7470,
      vnddotnueradotecelp9600,
      vnddotocteldotsbc,
      vnddotpresonusdotmultitrack,
      vnddotqcelp,
      vnddotrhetorexdot32kadpcm,
      vnddotrip,
      vnddotrnRealaudio,
      vnddotsealedmediadotsoftsealdotmpeg,
      vnddotvmxdotcvsd,
      vnddotwave,
      vorbis,
      vorbisConfig,
      wav,
      wave,
      webm,
      xAac,
      xAiff,
      xCaf,
      xFlac,
      xM4a,
      xMatroska,
      xMpegurl,
      xMsWax,
      xMsWma,
      xPnRealaudio,
      xPnRealaudioPlugin,
      xRealaudio,
      xTta,
      xWav,
      xm
    )
  }

  object chemical {
    lazy val xCdx: MediaType =
      MediaType("chemical", "x-cdx", compressible = false, binary = true, fileExtensions = List("cdx"))

    lazy val xCif: MediaType =
      MediaType("chemical", "x-cif", compressible = false, binary = true, fileExtensions = List("cif"))

    lazy val xCmdf: MediaType =
      MediaType("chemical", "x-cmdf", compressible = false, binary = true, fileExtensions = List("cmdf"))

    lazy val xCml: MediaType =
      MediaType("chemical", "x-cml", compressible = false, binary = true, fileExtensions = List("cml"))

    lazy val xCsml: MediaType =
      MediaType("chemical", "x-csml", compressible = false, binary = true, fileExtensions = List("csml"))

    lazy val xPdb: MediaType =
      MediaType("chemical", "x-pdb", compressible = false, binary = true)

    lazy val xXyz: MediaType =
      MediaType("chemical", "x-xyz", compressible = false, binary = true, fileExtensions = List("xyz"))

    def all: List[MediaType] = List(
      xCdx,
      xCif,
      xCmdf,
      xCml,
      xCsml,
      xPdb,
      xXyz
    )
  }

  object font {
    lazy val collection: MediaType =
      MediaType("font", "collection", compressible = false, binary = true, fileExtensions = List("ttc"))

    lazy val otf: MediaType =
      MediaType("font", "otf", compressible = true, binary = true, fileExtensions = List("otf"))

    lazy val sfnt: MediaType =
      MediaType("font", "sfnt", compressible = false, binary = true)

    lazy val ttf: MediaType =
      MediaType("font", "ttf", compressible = true, binary = true, fileExtensions = List("ttf"))

    lazy val woff: MediaType =
      MediaType("font", "woff", compressible = false, binary = true, fileExtensions = List("woff"))

    lazy val woff2: MediaType =
      MediaType("font", "woff2", compressible = false, binary = true, fileExtensions = List("woff2"))

    def all: List[MediaType] = List(
      collection,
      otf,
      sfnt,
      ttf,
      woff,
      woff2
    )
  }

  object image {
    lazy val aces: MediaType =
      MediaType("image", "aces", compressible = false, binary = true, fileExtensions = List("exr"))

    lazy val apng: MediaType =
      MediaType("image", "apng", compressible = false, binary = true, fileExtensions = List("apng"))

    lazy val avci: MediaType =
      MediaType("image", "avci", compressible = false, binary = true, fileExtensions = List("avci"))

    lazy val avcs: MediaType =
      MediaType("image", "avcs", compressible = false, binary = true, fileExtensions = List("avcs"))

    lazy val avif: MediaType =
      MediaType("image", "avif", compressible = false, binary = true, fileExtensions = List("avif"))

    lazy val bmp: MediaType =
      MediaType("image", "bmp", compressible = true, binary = true, fileExtensions = List("bmp", "dib"))

    lazy val cgm: MediaType =
      MediaType("image", "cgm", compressible = false, binary = true, fileExtensions = List("cgm"))

    lazy val dicomRle: MediaType =
      MediaType("image", "dicom-rle", compressible = false, binary = true, fileExtensions = List("drle"))

    lazy val dpx: MediaType =
      MediaType("image", "dpx", compressible = false, binary = true, fileExtensions = List("dpx"))

    lazy val emf: MediaType =
      MediaType("image", "emf", compressible = false, binary = true, fileExtensions = List("emf"))

    lazy val fits: MediaType =
      MediaType("image", "fits", compressible = false, binary = true, fileExtensions = List("fits"))

    lazy val g3fax: MediaType =
      MediaType("image", "g3fax", compressible = false, binary = true, fileExtensions = List("g3"))

    lazy val gif: MediaType =
      MediaType("image", "gif", compressible = false, binary = true, fileExtensions = List("gif"))

    lazy val heic: MediaType =
      MediaType("image", "heic", compressible = false, binary = true, fileExtensions = List("heic"))

    lazy val heicSequence: MediaType =
      MediaType("image", "heic-sequence", compressible = false, binary = true, fileExtensions = List("heics"))

    lazy val heif: MediaType =
      MediaType("image", "heif", compressible = false, binary = true, fileExtensions = List("heif"))

    lazy val heifSequence: MediaType =
      MediaType("image", "heif-sequence", compressible = false, binary = true, fileExtensions = List("heifs"))

    lazy val hej2k: MediaType =
      MediaType("image", "hej2k", compressible = false, binary = true, fileExtensions = List("hej2"))

    lazy val ief: MediaType =
      MediaType("image", "ief", compressible = false, binary = true, fileExtensions = List("ief"))

    lazy val j2c: MediaType =
      MediaType("image", "j2c", compressible = false, binary = true)

    lazy val jaii: MediaType =
      MediaType("image", "jaii", compressible = false, binary = true, fileExtensions = List("jaii"))

    lazy val jais: MediaType =
      MediaType("image", "jais", compressible = false, binary = true, fileExtensions = List("jais"))

    lazy val jls: MediaType =
      MediaType("image", "jls", compressible = false, binary = true, fileExtensions = List("jls"))

    lazy val jp2: MediaType =
      MediaType("image", "jp2", compressible = false, binary = true, fileExtensions = List("jp2", "jpg2"))

    lazy val jpeg: MediaType =
      MediaType("image", "jpeg", compressible = false, binary = true, fileExtensions = List("jpg", "jpeg", "jpe"))

    lazy val jph: MediaType =
      MediaType("image", "jph", compressible = false, binary = true, fileExtensions = List("jph"))

    lazy val jphc: MediaType =
      MediaType("image", "jphc", compressible = false, binary = true, fileExtensions = List("jhc"))

    lazy val jpm: MediaType =
      MediaType("image", "jpm", compressible = false, binary = true, fileExtensions = List("jpm", "jpgm"))

    lazy val jpx: MediaType =
      MediaType("image", "jpx", compressible = false, binary = true, fileExtensions = List("jpx", "jpf"))

    lazy val jxl: MediaType =
      MediaType("image", "jxl", compressible = false, binary = true, fileExtensions = List("jxl"))

    lazy val jxr: MediaType =
      MediaType("image", "jxr", compressible = false, binary = true, fileExtensions = List("jxr"))

    lazy val jxra: MediaType =
      MediaType("image", "jxra", compressible = false, binary = true, fileExtensions = List("jxra"))

    lazy val jxrs: MediaType =
      MediaType("image", "jxrs", compressible = false, binary = true, fileExtensions = List("jxrs"))

    lazy val jxs: MediaType =
      MediaType("image", "jxs", compressible = false, binary = true, fileExtensions = List("jxs"))

    lazy val jxsc: MediaType =
      MediaType("image", "jxsc", compressible = false, binary = true, fileExtensions = List("jxsc"))

    lazy val jxsi: MediaType =
      MediaType("image", "jxsi", compressible = false, binary = true, fileExtensions = List("jxsi"))

    lazy val jxss: MediaType =
      MediaType("image", "jxss", compressible = false, binary = true, fileExtensions = List("jxss"))

    lazy val ktx: MediaType =
      MediaType("image", "ktx", compressible = false, binary = true, fileExtensions = List("ktx"))

    lazy val ktx2: MediaType =
      MediaType("image", "ktx2", compressible = false, binary = true, fileExtensions = List("ktx2"))

    lazy val naplps: MediaType =
      MediaType("image", "naplps", compressible = false, binary = true)

    lazy val pjpeg: MediaType =
      MediaType("image", "pjpeg", compressible = false, binary = true, fileExtensions = List("jfif"))

    lazy val png: MediaType =
      MediaType("image", "png", compressible = false, binary = true, fileExtensions = List("png"))

    lazy val prsdotbtif: MediaType =
      MediaType("image", "prs.btif", compressible = false, binary = true, fileExtensions = List("btif", "btf"))

    lazy val prsdotpti: MediaType =
      MediaType("image", "prs.pti", compressible = false, binary = true, fileExtensions = List("pti"))

    lazy val pwgRaster: MediaType =
      MediaType("image", "pwg-raster", compressible = false, binary = true)

    lazy val sgi: MediaType =
      MediaType("image", "sgi", compressible = false, binary = true, fileExtensions = List("sgi"))

    lazy val svgplusxml: MediaType =
      MediaType("image", "svg+xml", compressible = true, binary = true, fileExtensions = List("svg", "svgz"))

    lazy val t38: MediaType =
      MediaType("image", "t38", compressible = false, binary = true, fileExtensions = List("t38"))

    lazy val tiff: MediaType =
      MediaType("image", "tiff", compressible = false, binary = true, fileExtensions = List("tif", "tiff"))

    lazy val tiffFx: MediaType =
      MediaType("image", "tiff-fx", compressible = false, binary = true, fileExtensions = List("tfx"))

    lazy val vnddotadobedotphotoshop: MediaType =
      MediaType("image", "vnd.adobe.photoshop", compressible = true, binary = true, fileExtensions = List("psd"))

    lazy val vnddotairzipdotacceleratordotazv: MediaType =
      MediaType(
        "image",
        "vnd.airzip.accelerator.azv",
        compressible = false,
        binary = true,
        fileExtensions = List("azv")
      )

    lazy val vnddotblockfactdotfacti: MediaType =
      MediaType("image", "vnd.blockfact.facti", compressible = false, binary = true, fileExtensions = List("facti"))

    lazy val vnddotclip: MediaType =
      MediaType("image", "vnd.clip", compressible = false, binary = true)

    lazy val vnddotcnsdotinf2: MediaType =
      MediaType("image", "vnd.cns.inf2", compressible = false, binary = true)

    lazy val vnddotdecedotgraphic: MediaType =
      MediaType(
        "image",
        "vnd.dece.graphic",
        compressible = false,
        binary = true,
        fileExtensions = List("uvi", "uvvi", "uvg", "uvvg")
      )

    lazy val vnddotdjvu: MediaType =
      MediaType("image", "vnd.djvu", compressible = false, binary = true, fileExtensions = List("djvu", "djv"))

    lazy val vnddotdvbdotsubtitle: MediaType =
      MediaType("image", "vnd.dvb.subtitle", compressible = false, binary = true, fileExtensions = List("sub"))

    lazy val vnddotdwg: MediaType =
      MediaType("image", "vnd.dwg", compressible = false, binary = true, fileExtensions = List("dwg"))

    lazy val vnddotdxf: MediaType =
      MediaType("image", "vnd.dxf", compressible = false, binary = true, fileExtensions = List("dxf"))

    lazy val vnddotfastbidsheet: MediaType =
      MediaType("image", "vnd.fastbidsheet", compressible = false, binary = true, fileExtensions = List("fbs"))

    lazy val vnddotfpx: MediaType =
      MediaType("image", "vnd.fpx", compressible = false, binary = true, fileExtensions = List("fpx"))

    lazy val vnddotfst: MediaType =
      MediaType("image", "vnd.fst", compressible = false, binary = true, fileExtensions = List("fst"))

    lazy val vnddotfujixeroxdotedmicsMmr: MediaType =
      MediaType("image", "vnd.fujixerox.edmics-mmr", compressible = false, binary = true, fileExtensions = List("mmr"))

    lazy val vnddotfujixeroxdotedmicsRlc: MediaType =
      MediaType("image", "vnd.fujixerox.edmics-rlc", compressible = false, binary = true, fileExtensions = List("rlc"))

    lazy val vnddotglobalgraphicsdotpgb: MediaType =
      MediaType("image", "vnd.globalgraphics.pgb", compressible = false, binary = true)

    lazy val vnddotmicrosoftdoticon: MediaType =
      MediaType("image", "vnd.microsoft.icon", compressible = true, binary = true, fileExtensions = List("ico"))

    lazy val vnddotmix: MediaType =
      MediaType("image", "vnd.mix", compressible = false, binary = true)

    lazy val vnddotmozilladotapng: MediaType =
      MediaType("image", "vnd.mozilla.apng", compressible = false, binary = true)

    lazy val vnddotmsDds: MediaType =
      MediaType("image", "vnd.ms-dds", compressible = true, binary = true, fileExtensions = List("dds"))

    lazy val vnddotmsModi: MediaType =
      MediaType("image", "vnd.ms-modi", compressible = false, binary = true, fileExtensions = List("mdi"))

    lazy val vnddotmsPhoto: MediaType =
      MediaType("image", "vnd.ms-photo", compressible = false, binary = true, fileExtensions = List("wdp"))

    lazy val vnddotnetFpx: MediaType =
      MediaType("image", "vnd.net-fpx", compressible = false, binary = true, fileExtensions = List("npx"))

    lazy val vnddotpcodotb16: MediaType =
      MediaType("image", "vnd.pco.b16", compressible = false, binary = true, fileExtensions = List("b16"))

    lazy val vnddotradiance: MediaType =
      MediaType("image", "vnd.radiance", compressible = false, binary = true)

    lazy val vnddotsealeddotpng: MediaType =
      MediaType("image", "vnd.sealed.png", compressible = false, binary = true)

    lazy val vnddotsealedmediadotsoftsealdotgif: MediaType =
      MediaType("image", "vnd.sealedmedia.softseal.gif", compressible = false, binary = true)

    lazy val vnddotsealedmediadotsoftsealdotjpg: MediaType =
      MediaType("image", "vnd.sealedmedia.softseal.jpg", compressible = false, binary = true)

    lazy val vnddotsvf: MediaType =
      MediaType("image", "vnd.svf", compressible = false, binary = true)

    lazy val vnddottencentdottap: MediaType =
      MediaType("image", "vnd.tencent.tap", compressible = false, binary = true, fileExtensions = List("tap"))

    lazy val vnddotvalvedotsourcedottexture: MediaType =
      MediaType("image", "vnd.valve.source.texture", compressible = false, binary = true, fileExtensions = List("vtf"))

    lazy val vnddotwapdotwbmp: MediaType =
      MediaType("image", "vnd.wap.wbmp", compressible = false, binary = true, fileExtensions = List("wbmp"))

    lazy val vnddotxiff: MediaType =
      MediaType("image", "vnd.xiff", compressible = false, binary = true, fileExtensions = List("xif"))

    lazy val vnddotzbrushdotpcx: MediaType =
      MediaType("image", "vnd.zbrush.pcx", compressible = false, binary = true, fileExtensions = List("pcx"))

    lazy val webp: MediaType =
      MediaType("image", "webp", compressible = false, binary = true, fileExtensions = List("webp"))

    lazy val wmf: MediaType =
      MediaType("image", "wmf", compressible = false, binary = true, fileExtensions = List("wmf"))

    lazy val x3ds: MediaType =
      MediaType("image", "x-3ds", compressible = false, binary = true, fileExtensions = List("3ds"))

    lazy val xAdobeDng: MediaType =
      MediaType("image", "x-adobe-dng", compressible = false, binary = true, fileExtensions = List("dng"))

    lazy val xCmuRaster: MediaType =
      MediaType("image", "x-cmu-raster", compressible = false, binary = true, fileExtensions = List("ras"))

    lazy val xCmx: MediaType =
      MediaType("image", "x-cmx", compressible = false, binary = true, fileExtensions = List("cmx"))

    lazy val xEmf: MediaType =
      MediaType("image", "x-emf", compressible = false, binary = true)

    lazy val xFreehand: MediaType =
      MediaType(
        "image",
        "x-freehand",
        compressible = false,
        binary = true,
        fileExtensions = List("fh", "fhc", "fh4", "fh5", "fh7")
      )

    lazy val xIcon: MediaType =
      MediaType("image", "x-icon", compressible = true, binary = true, fileExtensions = List("ico"))

    lazy val xJng: MediaType =
      MediaType("image", "x-jng", compressible = false, binary = true, fileExtensions = List("jng"))

    lazy val xMrsidImage: MediaType =
      MediaType("image", "x-mrsid-image", compressible = false, binary = true, fileExtensions = List("sid"))

    lazy val xMsBmp: MediaType =
      MediaType("image", "x-ms-bmp", compressible = true, binary = true, fileExtensions = List("bmp"))

    lazy val xPcx: MediaType =
      MediaType("image", "x-pcx", compressible = false, binary = true, fileExtensions = List("pcx"))

    lazy val xPict: MediaType =
      MediaType("image", "x-pict", compressible = false, binary = true, fileExtensions = List("pic", "pct"))

    lazy val xPortableAnymap: MediaType =
      MediaType("image", "x-portable-anymap", compressible = false, binary = true, fileExtensions = List("pnm"))

    lazy val xPortableBitmap: MediaType =
      MediaType("image", "x-portable-bitmap", compressible = false, binary = true, fileExtensions = List("pbm"))

    lazy val xPortableGraymap: MediaType =
      MediaType("image", "x-portable-graymap", compressible = false, binary = true, fileExtensions = List("pgm"))

    lazy val xPortablePixmap: MediaType =
      MediaType("image", "x-portable-pixmap", compressible = false, binary = true, fileExtensions = List("ppm"))

    lazy val xRgb: MediaType =
      MediaType("image", "x-rgb", compressible = false, binary = true, fileExtensions = List("rgb"))

    lazy val xTga: MediaType =
      MediaType("image", "x-tga", compressible = false, binary = true, fileExtensions = List("tga"))

    lazy val xWmf: MediaType =
      MediaType("image", "x-wmf", compressible = false, binary = true)

    lazy val xXbitmap: MediaType =
      MediaType("image", "x-xbitmap", compressible = false, binary = true, fileExtensions = List("xbm"))

    lazy val xXcf: MediaType =
      MediaType("image", "x-xcf", compressible = false, binary = true)

    lazy val xXpixmap: MediaType =
      MediaType("image", "x-xpixmap", compressible = false, binary = true, fileExtensions = List("xpm"))

    lazy val xXwindowdump: MediaType =
      MediaType("image", "x-xwindowdump", compressible = false, binary = true, fileExtensions = List("xwd"))

    def all: List[MediaType] = List(
      aces,
      apng,
      avci,
      avcs,
      avif,
      bmp,
      cgm,
      dicomRle,
      dpx,
      emf,
      fits,
      g3fax,
      gif,
      heic,
      heicSequence,
      heif,
      heifSequence,
      hej2k,
      ief,
      j2c,
      jaii,
      jais,
      jls,
      jp2,
      jpeg,
      jph,
      jphc,
      jpm,
      jpx,
      jxl,
      jxr,
      jxra,
      jxrs,
      jxs,
      jxsc,
      jxsi,
      jxss,
      ktx,
      ktx2,
      naplps,
      pjpeg,
      png,
      prsdotbtif,
      prsdotpti,
      pwgRaster,
      sgi,
      svgplusxml,
      t38,
      tiff,
      tiffFx,
      vnddotadobedotphotoshop,
      vnddotairzipdotacceleratordotazv,
      vnddotblockfactdotfacti,
      vnddotclip,
      vnddotcnsdotinf2,
      vnddotdecedotgraphic,
      vnddotdjvu,
      vnddotdvbdotsubtitle,
      vnddotdwg,
      vnddotdxf,
      vnddotfastbidsheet,
      vnddotfpx,
      vnddotfst,
      vnddotfujixeroxdotedmicsMmr,
      vnddotfujixeroxdotedmicsRlc,
      vnddotglobalgraphicsdotpgb,
      vnddotmicrosoftdoticon,
      vnddotmix,
      vnddotmozilladotapng,
      vnddotmsDds,
      vnddotmsModi,
      vnddotmsPhoto,
      vnddotnetFpx,
      vnddotpcodotb16,
      vnddotradiance,
      vnddotsealeddotpng,
      vnddotsealedmediadotsoftsealdotgif,
      vnddotsealedmediadotsoftsealdotjpg,
      vnddotsvf,
      vnddottencentdottap,
      vnddotvalvedotsourcedottexture,
      vnddotwapdotwbmp,
      vnddotxiff,
      vnddotzbrushdotpcx,
      webp,
      wmf,
      x3ds,
      xAdobeDng,
      xCmuRaster,
      xCmx,
      xEmf,
      xFreehand,
      xIcon,
      xJng,
      xMrsidImage,
      xMsBmp,
      xPcx,
      xPict,
      xPortableAnymap,
      xPortableBitmap,
      xPortableGraymap,
      xPortablePixmap,
      xRgb,
      xTga,
      xWmf,
      xXbitmap,
      xXcf,
      xXpixmap,
      xXwindowdump
    )
  }

  object message {
    lazy val bhttp: MediaType =
      MediaType("message", "bhttp", compressible = false, binary = true)

    lazy val cpim: MediaType =
      MediaType("message", "cpim", compressible = false, binary = true)

    lazy val deliveryStatus: MediaType =
      MediaType("message", "delivery-status", compressible = false, binary = true)

    lazy val dispositionNotification: MediaType =
      MediaType("message", "disposition-notification", compressible = false, binary = true)

    lazy val externalBody: MediaType =
      MediaType("message", "external-body", compressible = false, binary = true)

    lazy val feedbackReport: MediaType =
      MediaType("message", "feedback-report", compressible = false, binary = true)

    lazy val global: MediaType =
      MediaType("message", "global", compressible = false, binary = true, fileExtensions = List("u8msg"))

    lazy val globalDeliveryStatus: MediaType =
      MediaType(
        "message",
        "global-delivery-status",
        compressible = false,
        binary = true,
        fileExtensions = List("u8dsn")
      )

    lazy val globalDispositionNotification: MediaType =
      MediaType(
        "message",
        "global-disposition-notification",
        compressible = false,
        binary = true,
        fileExtensions = List("u8mdn")
      )

    lazy val globalHeaders: MediaType =
      MediaType("message", "global-headers", compressible = false, binary = true, fileExtensions = List("u8hdr"))

    lazy val http: MediaType =
      MediaType("message", "http", compressible = false, binary = true)

    lazy val imdnplusxml: MediaType =
      MediaType("message", "imdn+xml", compressible = true, binary = false)

    lazy val mls: MediaType =
      MediaType("message", "mls", compressible = false, binary = true)

    lazy val news: MediaType =
      MediaType("message", "news", compressible = false, binary = true)

    lazy val ohttpReq: MediaType =
      MediaType("message", "ohttp-req", compressible = false, binary = true)

    lazy val ohttpRes: MediaType =
      MediaType("message", "ohttp-res", compressible = false, binary = true)

    lazy val partial: MediaType =
      MediaType("message", "partial", compressible = false, binary = true)

    lazy val rfc822: MediaType =
      MediaType(
        "message",
        "rfc822",
        compressible = true,
        binary = false,
        fileExtensions = List("eml", "mime", "mht", "mhtml")
      )

    lazy val sHttp: MediaType =
      MediaType("message", "s-http", compressible = false, binary = true)

    lazy val sip: MediaType =
      MediaType("message", "sip", compressible = false, binary = true)

    lazy val sipfrag: MediaType =
      MediaType("message", "sipfrag", compressible = false, binary = true)

    lazy val trackingStatus: MediaType =
      MediaType("message", "tracking-status", compressible = false, binary = true)

    lazy val vnddotsidotsimp: MediaType =
      MediaType("message", "vnd.si.simp", compressible = false, binary = true)

    lazy val vnddotwfadotwsc: MediaType =
      MediaType("message", "vnd.wfa.wsc", compressible = false, binary = true, fileExtensions = List("wsc"))

    def all: List[MediaType] = List(
      bhttp,
      cpim,
      deliveryStatus,
      dispositionNotification,
      externalBody,
      feedbackReport,
      global,
      globalDeliveryStatus,
      globalDispositionNotification,
      globalHeaders,
      http,
      imdnplusxml,
      mls,
      news,
      ohttpReq,
      ohttpRes,
      partial,
      rfc822,
      sHttp,
      sip,
      sipfrag,
      trackingStatus,
      vnddotsidotsimp,
      vnddotwfadotwsc
    )
  }

  object model {
    lazy val _3mf: MediaType =
      MediaType("model", "3mf", compressible = false, binary = true, fileExtensions = List("3mf"))

    lazy val e57: MediaType =
      MediaType("model", "e57", compressible = false, binary = true)

    lazy val gltfplusjson: MediaType =
      MediaType("model", "gltf+json", compressible = true, binary = true, fileExtensions = List("gltf"))

    lazy val gltfBinary: MediaType =
      MediaType("model", "gltf-binary", compressible = true, binary = true, fileExtensions = List("glb"))

    lazy val iges: MediaType =
      MediaType("model", "iges", compressible = false, binary = true, fileExtensions = List("igs", "iges"))

    lazy val jt: MediaType =
      MediaType("model", "jt", compressible = false, binary = true, fileExtensions = List("jt"))

    lazy val mesh: MediaType =
      MediaType("model", "mesh", compressible = false, binary = true, fileExtensions = List("msh", "mesh", "silo"))

    lazy val mtl: MediaType =
      MediaType("model", "mtl", compressible = false, binary = true, fileExtensions = List("mtl"))

    lazy val obj: MediaType =
      MediaType("model", "obj", compressible = false, binary = true, fileExtensions = List("obj"))

    lazy val prc: MediaType =
      MediaType("model", "prc", compressible = false, binary = true, fileExtensions = List("prc"))

    lazy val step: MediaType =
      MediaType(
        "model",
        "step",
        compressible = false,
        binary = true,
        fileExtensions = List("step", "stp", "stpnc", "p21", "210")
      )

    lazy val stepplusxml: MediaType =
      MediaType("model", "step+xml", compressible = true, binary = true, fileExtensions = List("stpx"))

    lazy val steppluszip: MediaType =
      MediaType("model", "step+zip", compressible = false, binary = true, fileExtensions = List("stpz"))

    lazy val stepXmlpluszip: MediaType =
      MediaType("model", "step-xml+zip", compressible = false, binary = true, fileExtensions = List("stpxz"))

    lazy val stl: MediaType =
      MediaType("model", "stl", compressible = false, binary = true, fileExtensions = List("stl"))

    lazy val u3d: MediaType =
      MediaType("model", "u3d", compressible = false, binary = true, fileExtensions = List("u3d"))

    lazy val vnddotbary: MediaType =
      MediaType("model", "vnd.bary", compressible = false, binary = true, fileExtensions = List("bary"))

    lazy val vnddotcld: MediaType =
      MediaType("model", "vnd.cld", compressible = false, binary = true, fileExtensions = List("cld"))

    lazy val vnddotcolladaplusxml: MediaType =
      MediaType("model", "vnd.collada+xml", compressible = true, binary = true, fileExtensions = List("dae"))

    lazy val vnddotdwf: MediaType =
      MediaType("model", "vnd.dwf", compressible = false, binary = true, fileExtensions = List("dwf"))

    lazy val vnddotflatlanddot3dml: MediaType =
      MediaType("model", "vnd.flatland.3dml", compressible = false, binary = true)

    lazy val vnddotgdl: MediaType =
      MediaType("model", "vnd.gdl", compressible = false, binary = true, fileExtensions = List("gdl"))

    lazy val vnddotgsGdl: MediaType =
      MediaType("model", "vnd.gs-gdl", compressible = false, binary = true)

    lazy val vnddotgsdotgdl: MediaType =
      MediaType("model", "vnd.gs.gdl", compressible = false, binary = true)

    lazy val vnddotgtw: MediaType =
      MediaType("model", "vnd.gtw", compressible = false, binary = true, fileExtensions = List("gtw"))

    lazy val vnddotmomlplusxml: MediaType =
      MediaType("model", "vnd.moml+xml", compressible = true, binary = true)

    lazy val vnddotmts: MediaType =
      MediaType("model", "vnd.mts", compressible = false, binary = true, fileExtensions = List("mts"))

    lazy val vnddotopengex: MediaType =
      MediaType("model", "vnd.opengex", compressible = false, binary = true, fileExtensions = List("ogex"))

    lazy val vnddotparasoliddottransmitdotbinary: MediaType =
      MediaType(
        "model",
        "vnd.parasolid.transmit.binary",
        compressible = false,
        binary = true,
        fileExtensions = List("x_b")
      )

    lazy val vnddotparasoliddottransmitdottext: MediaType =
      MediaType(
        "model",
        "vnd.parasolid.transmit.text",
        compressible = false,
        binary = true,
        fileExtensions = List("x_t")
      )

    lazy val vnddotpythadotpyox: MediaType =
      MediaType("model", "vnd.pytha.pyox", compressible = false, binary = true, fileExtensions = List("pyo", "pyox"))

    lazy val vnddotrosettedotannotatedDataModel: MediaType =
      MediaType("model", "vnd.rosette.annotated-data-model", compressible = false, binary = true)

    lazy val vnddotsapdotvds: MediaType =
      MediaType("model", "vnd.sap.vds", compressible = false, binary = true, fileExtensions = List("vds"))

    lazy val vnddotusda: MediaType =
      MediaType("model", "vnd.usda", compressible = false, binary = true, fileExtensions = List("usda"))

    lazy val vnddotusdzpluszip: MediaType =
      MediaType("model", "vnd.usdz+zip", compressible = false, binary = true, fileExtensions = List("usdz"))

    lazy val vnddotvalvedotsourcedotcompiledMap: MediaType =
      MediaType(
        "model",
        "vnd.valve.source.compiled-map",
        compressible = false,
        binary = true,
        fileExtensions = List("bsp")
      )

    lazy val vnddotvtu: MediaType =
      MediaType("model", "vnd.vtu", compressible = false, binary = true, fileExtensions = List("vtu"))

    lazy val vrml: MediaType =
      MediaType("model", "vrml", compressible = false, binary = true, fileExtensions = List("wrl", "vrml"))

    lazy val x3dplusbinary: MediaType =
      MediaType("model", "x3d+binary", compressible = false, binary = true, fileExtensions = List("x3db", "x3dbz"))

    lazy val x3dplusfastinfoset: MediaType =
      MediaType("model", "x3d+fastinfoset", compressible = false, binary = true, fileExtensions = List("x3db"))

    lazy val x3dplusvrml: MediaType =
      MediaType("model", "x3d+vrml", compressible = false, binary = true, fileExtensions = List("x3dv", "x3dvz"))

    lazy val x3dplusxml: MediaType =
      MediaType("model", "x3d+xml", compressible = true, binary = true, fileExtensions = List("x3d", "x3dz"))

    lazy val x3dVrml: MediaType =
      MediaType("model", "x3d-vrml", compressible = false, binary = true, fileExtensions = List("x3dv"))

    def all: List[MediaType] = List(
      _3mf,
      e57,
      gltfplusjson,
      gltfBinary,
      iges,
      jt,
      mesh,
      mtl,
      obj,
      prc,
      step,
      stepplusxml,
      steppluszip,
      stepXmlpluszip,
      stl,
      u3d,
      vnddotbary,
      vnddotcld,
      vnddotcolladaplusxml,
      vnddotdwf,
      vnddotflatlanddot3dml,
      vnddotgdl,
      vnddotgsGdl,
      vnddotgsdotgdl,
      vnddotgtw,
      vnddotmomlplusxml,
      vnddotmts,
      vnddotopengex,
      vnddotparasoliddottransmitdotbinary,
      vnddotparasoliddottransmitdottext,
      vnddotpythadotpyox,
      vnddotrosettedotannotatedDataModel,
      vnddotsapdotvds,
      vnddotusda,
      vnddotusdzpluszip,
      vnddotvalvedotsourcedotcompiledMap,
      vnddotvtu,
      vrml,
      x3dplusbinary,
      x3dplusfastinfoset,
      x3dplusvrml,
      x3dplusxml,
      x3dVrml
    )
  }

  object multipart {
    lazy val alternative: MediaType =
      MediaType("multipart", "alternative", compressible = false, binary = true)

    lazy val appledouble: MediaType =
      MediaType("multipart", "appledouble", compressible = false, binary = true)

    lazy val byteranges: MediaType =
      MediaType("multipart", "byteranges", compressible = false, binary = true)

    lazy val digest: MediaType =
      MediaType("multipart", "digest", compressible = false, binary = true)

    lazy val encrypted: MediaType =
      MediaType("multipart", "encrypted", compressible = false, binary = true)

    lazy val formData: MediaType =
      MediaType("multipart", "form-data", compressible = false, binary = true)

    lazy val headerSet: MediaType =
      MediaType("multipart", "header-set", compressible = false, binary = true)

    lazy val mixed: MediaType =
      MediaType("multipart", "mixed", compressible = false, binary = true)

    lazy val multilingual: MediaType =
      MediaType("multipart", "multilingual", compressible = false, binary = true)

    lazy val parallel: MediaType =
      MediaType("multipart", "parallel", compressible = false, binary = true)

    lazy val related: MediaType =
      MediaType("multipart", "related", compressible = false, binary = true)

    lazy val report: MediaType =
      MediaType("multipart", "report", compressible = false, binary = true)

    lazy val signed: MediaType =
      MediaType("multipart", "signed", compressible = false, binary = true)

    lazy val vnddotbintdotmedPlus: MediaType =
      MediaType("multipart", "vnd.bint.med-plus", compressible = false, binary = true)

    lazy val voiceMessage: MediaType =
      MediaType("multipart", "voice-message", compressible = false, binary = true)

    lazy val xMixedReplace: MediaType =
      MediaType("multipart", "x-mixed-replace", compressible = false, binary = true)

    def all: List[MediaType] = List(
      alternative,
      appledouble,
      byteranges,
      digest,
      encrypted,
      formData,
      headerSet,
      mixed,
      multilingual,
      parallel,
      related,
      report,
      signed,
      vnddotbintdotmedPlus,
      voiceMessage,
      xMixedReplace
    )
  }

  object text {
    lazy val _1dInterleavedParityfec: MediaType =
      MediaType("text", "1d-interleaved-parityfec", compressible = false, binary = false)

    lazy val cacheManifest: MediaType =
      MediaType(
        "text",
        "cache-manifest",
        compressible = true,
        binary = false,
        fileExtensions = List("appcache", "manifest")
      )

    lazy val calendar: MediaType =
      MediaType("text", "calendar", compressible = false, binary = false, fileExtensions = List("ics", "ifb"))

    lazy val cmd: MediaType =
      MediaType("text", "cmd", compressible = true, binary = false)

    lazy val coffeescript: MediaType =
      MediaType(
        "text",
        "coffeescript",
        compressible = false,
        binary = false,
        fileExtensions = List("coffee", "litcoffee")
      )

    lazy val cql: MediaType =
      MediaType("text", "cql", compressible = false, binary = false)

    lazy val cqlExpression: MediaType =
      MediaType("text", "cql-expression", compressible = false, binary = false)

    lazy val cqlIdentifier: MediaType =
      MediaType("text", "cql-identifier", compressible = false, binary = false)

    lazy val css: MediaType =
      MediaType("text", "css", compressible = true, binary = false, fileExtensions = List("css"))

    lazy val csv: MediaType =
      MediaType("text", "csv", compressible = true, binary = false, fileExtensions = List("csv"))

    lazy val csvSchema: MediaType =
      MediaType("text", "csv-schema", compressible = false, binary = false)

    lazy val directory: MediaType =
      MediaType("text", "directory", compressible = false, binary = false)

    lazy val dns: MediaType =
      MediaType("text", "dns", compressible = false, binary = false)

    lazy val ecmascript: MediaType =
      MediaType("text", "ecmascript", compressible = false, binary = false)

    lazy val encaprtp: MediaType =
      MediaType("text", "encaprtp", compressible = false, binary = false)

    lazy val enriched: MediaType =
      MediaType("text", "enriched", compressible = false, binary = false)

    lazy val fhirpath: MediaType =
      MediaType("text", "fhirpath", compressible = false, binary = false)

    lazy val flexfec: MediaType =
      MediaType("text", "flexfec", compressible = false, binary = false)

    lazy val fwdred: MediaType =
      MediaType("text", "fwdred", compressible = false, binary = false)

    lazy val gff3: MediaType =
      MediaType("text", "gff3", compressible = false, binary = false)

    lazy val grammarRefList: MediaType =
      MediaType("text", "grammar-ref-list", compressible = false, binary = false)

    lazy val hl7v2: MediaType =
      MediaType("text", "hl7v2", compressible = false, binary = false)

    lazy val html: MediaType =
      MediaType("text", "html", compressible = true, binary = false, fileExtensions = List("html", "htm", "shtml"))

    lazy val jade: MediaType =
      MediaType("text", "jade", compressible = false, binary = false, fileExtensions = List("jade"))

    lazy val javascript: MediaType =
      MediaType("text", "javascript", compressible = true, binary = false, fileExtensions = List("js", "mjs"))

    lazy val jcrCnd: MediaType =
      MediaType("text", "jcr-cnd", compressible = false, binary = false)

    lazy val jsx: MediaType =
      MediaType("text", "jsx", compressible = true, binary = false, fileExtensions = List("jsx"))

    lazy val less: MediaType =
      MediaType("text", "less", compressible = true, binary = false, fileExtensions = List("less"))

    lazy val markdown: MediaType =
      MediaType("text", "markdown", compressible = true, binary = false, fileExtensions = List("md", "markdown"))

    lazy val mathml: MediaType =
      MediaType("text", "mathml", compressible = false, binary = false, fileExtensions = List("mml"))

    lazy val mdx: MediaType =
      MediaType("text", "mdx", compressible = true, binary = false, fileExtensions = List("mdx"))

    lazy val mizar: MediaType =
      MediaType("text", "mizar", compressible = false, binary = false)

    lazy val n3: MediaType =
      MediaType("text", "n3", compressible = true, binary = false, fileExtensions = List("n3"))

    lazy val org: MediaType =
      MediaType("text", "org", compressible = false, binary = false)

    lazy val parameters: MediaType =
      MediaType("text", "parameters", compressible = false, binary = false)

    lazy val parityfec: MediaType =
      MediaType("text", "parityfec", compressible = false, binary = false)

    lazy val plain: MediaType =
      MediaType(
        "text",
        "plain",
        compressible = true,
        binary = false,
        fileExtensions = List("txt", "text", "conf", "def", "list", "log", "in", "ini")
      )

    lazy val provenanceNotation: MediaType =
      MediaType("text", "provenance-notation", compressible = false, binary = false)

    lazy val prsdotfallensteindotrst: MediaType =
      MediaType("text", "prs.fallenstein.rst", compressible = false, binary = false)

    lazy val prsdotlinesdottag: MediaType =
      MediaType("text", "prs.lines.tag", compressible = false, binary = false, fileExtensions = List("dsc"))

    lazy val prsdotpropdotlogic: MediaType =
      MediaType("text", "prs.prop.logic", compressible = false, binary = false)

    lazy val prsdottexi: MediaType =
      MediaType("text", "prs.texi", compressible = false, binary = false)

    lazy val raptorfec: MediaType =
      MediaType("text", "raptorfec", compressible = false, binary = false)

    lazy val red: MediaType =
      MediaType("text", "red", compressible = false, binary = false)

    lazy val rfc822Headers: MediaType =
      MediaType("text", "rfc822-headers", compressible = false, binary = false)

    lazy val richtext: MediaType =
      MediaType("text", "richtext", compressible = true, binary = false, fileExtensions = List("rtx"))

    lazy val rtf: MediaType =
      MediaType("text", "rtf", compressible = true, binary = false, fileExtensions = List("rtf"))

    lazy val rtpEncAescm128: MediaType =
      MediaType("text", "rtp-enc-aescm128", compressible = false, binary = false)

    lazy val rtploopback: MediaType =
      MediaType("text", "rtploopback", compressible = false, binary = false)

    lazy val rtx: MediaType =
      MediaType("text", "rtx", compressible = false, binary = false)

    lazy val sgml: MediaType =
      MediaType("text", "sgml", compressible = false, binary = false, fileExtensions = List("sgml", "sgm"))

    lazy val shaclc: MediaType =
      MediaType("text", "shaclc", compressible = false, binary = false)

    lazy val shex: MediaType =
      MediaType("text", "shex", compressible = false, binary = false, fileExtensions = List("shex"))

    lazy val slim: MediaType =
      MediaType("text", "slim", compressible = false, binary = false, fileExtensions = List("slim", "slm"))

    lazy val spdx: MediaType =
      MediaType("text", "spdx", compressible = false, binary = false, fileExtensions = List("spdx"))

    lazy val strings: MediaType =
      MediaType("text", "strings", compressible = false, binary = false)

    lazy val stylus: MediaType =
      MediaType("text", "stylus", compressible = false, binary = false, fileExtensions = List("stylus", "styl"))

    lazy val t140: MediaType =
      MediaType("text", "t140", compressible = false, binary = false)

    lazy val tabSeparatedValues: MediaType =
      MediaType("text", "tab-separated-values", compressible = true, binary = false, fileExtensions = List("tsv"))

    lazy val troff: MediaType =
      MediaType(
        "text",
        "troff",
        compressible = false,
        binary = false,
        fileExtensions = List("t", "tr", "roff", "man", "me", "ms")
      )

    lazy val turtle: MediaType =
      MediaType("text", "turtle", compressible = false, binary = false, fileExtensions = List("ttl"))

    lazy val ulpfec: MediaType =
      MediaType("text", "ulpfec", compressible = false, binary = false)

    lazy val uriList: MediaType =
      MediaType("text", "uri-list", compressible = true, binary = false, fileExtensions = List("uri", "uris", "urls"))

    lazy val vcard: MediaType =
      MediaType("text", "vcard", compressible = true, binary = false, fileExtensions = List("vcard"))

    lazy val vnddota: MediaType =
      MediaType("text", "vnd.a", compressible = false, binary = false)

    lazy val vnddotabc: MediaType =
      MediaType("text", "vnd.abc", compressible = false, binary = false)

    lazy val vnddotasciiArt: MediaType =
      MediaType("text", "vnd.ascii-art", compressible = false, binary = false)

    lazy val vnddotcurl: MediaType =
      MediaType("text", "vnd.curl", compressible = false, binary = false, fileExtensions = List("curl"))

    lazy val vnddotcurldotdcurl: MediaType =
      MediaType("text", "vnd.curl.dcurl", compressible = false, binary = false, fileExtensions = List("dcurl"))

    lazy val vnddotcurldotmcurl: MediaType =
      MediaType("text", "vnd.curl.mcurl", compressible = false, binary = false, fileExtensions = List("mcurl"))

    lazy val vnddotcurldotscurl: MediaType =
      MediaType("text", "vnd.curl.scurl", compressible = false, binary = false, fileExtensions = List("scurl"))

    lazy val vnddotdebiandotcopyright: MediaType =
      MediaType("text", "vnd.debian.copyright", compressible = false, binary = false)

    lazy val vnddotdmclientscript: MediaType =
      MediaType("text", "vnd.dmclientscript", compressible = false, binary = false)

    lazy val vnddotdvbdotsubtitle: MediaType =
      MediaType("text", "vnd.dvb.subtitle", compressible = false, binary = false, fileExtensions = List("sub"))

    lazy val vnddotesmertecdotthemeDescriptor: MediaType =
      MediaType("text", "vnd.esmertec.theme-descriptor", compressible = false, binary = false)

    lazy val vnddotexchangeable: MediaType =
      MediaType("text", "vnd.exchangeable", compressible = false, binary = false)

    lazy val vnddotfamilysearchdotgedcom: MediaType =
      MediaType("text", "vnd.familysearch.gedcom", compressible = false, binary = false, fileExtensions = List("ged"))

    lazy val vnddotficlabdotflt: MediaType =
      MediaType("text", "vnd.ficlab.flt", compressible = false, binary = false)

    lazy val vnddotfly: MediaType =
      MediaType("text", "vnd.fly", compressible = false, binary = false, fileExtensions = List("fly"))

    lazy val vnddotfmidotflexstor: MediaType =
      MediaType("text", "vnd.fmi.flexstor", compressible = false, binary = false, fileExtensions = List("flx"))

    lazy val vnddotgml: MediaType =
      MediaType("text", "vnd.gml", compressible = false, binary = false)

    lazy val vnddotgraphviz: MediaType =
      MediaType("text", "vnd.graphviz", compressible = false, binary = false, fileExtensions = List("gv"))

    lazy val vnddothans: MediaType =
      MediaType("text", "vnd.hans", compressible = false, binary = false)

    lazy val vnddothgl: MediaType =
      MediaType("text", "vnd.hgl", compressible = false, binary = false)

    lazy val vnddotin3ddot3dml: MediaType =
      MediaType("text", "vnd.in3d.3dml", compressible = false, binary = false, fileExtensions = List("3dml"))

    lazy val vnddotin3ddotspot: MediaType =
      MediaType("text", "vnd.in3d.spot", compressible = false, binary = false, fileExtensions = List("spot"))

    lazy val vnddotiptcdotnewsml: MediaType =
      MediaType("text", "vnd.iptc.newsml", compressible = false, binary = false)

    lazy val vnddotiptcdotnitf: MediaType =
      MediaType("text", "vnd.iptc.nitf", compressible = false, binary = false)

    lazy val vnddotlatexZ: MediaType =
      MediaType("text", "vnd.latex-z", compressible = false, binary = false)

    lazy val vnddotmotoroladotreflex: MediaType =
      MediaType("text", "vnd.motorola.reflex", compressible = false, binary = false)

    lazy val vnddotmsMediapackage: MediaType =
      MediaType("text", "vnd.ms-mediapackage", compressible = false, binary = false)

    lazy val vnddotnet2phonedotcommcenterdotcommand: MediaType =
      MediaType("text", "vnd.net2phone.commcenter.command", compressible = false, binary = false)

    lazy val vnddotradisysdotmsmlBasicLayout: MediaType =
      MediaType("text", "vnd.radisys.msml-basic-layout", compressible = false, binary = false)

    lazy val vnddotsenxdotwarpscript: MediaType =
      MediaType("text", "vnd.senx.warpscript", compressible = false, binary = false)

    lazy val vnddotsidoturicatalogue: MediaType =
      MediaType("text", "vnd.si.uricatalogue", compressible = false, binary = false)

    lazy val vnddotsosi: MediaType =
      MediaType("text", "vnd.sosi", compressible = false, binary = false)

    lazy val vnddotsundotj2medotappDescriptor: MediaType =
      MediaType(
        "text",
        "vnd.sun.j2me.app-descriptor",
        compressible = false,
        binary = false,
        fileExtensions = List("jad")
      )

    lazy val vnddottrolltechdotlinguist: MediaType =
      MediaType("text", "vnd.trolltech.linguist", compressible = false, binary = false)

    lazy val vnddottypst: MediaType =
      MediaType("text", "vnd.typst", compressible = false, binary = false)

    lazy val vnddotvcf: MediaType =
      MediaType("text", "vnd.vcf", compressible = false, binary = false)

    lazy val vnddotwapdotsi: MediaType =
      MediaType("text", "vnd.wap.si", compressible = false, binary = false)

    lazy val vnddotwapdotsl: MediaType =
      MediaType("text", "vnd.wap.sl", compressible = false, binary = false)

    lazy val vnddotwapdotwml: MediaType =
      MediaType("text", "vnd.wap.wml", compressible = false, binary = false, fileExtensions = List("wml"))

    lazy val vnddotwapdotwmlscript: MediaType =
      MediaType("text", "vnd.wap.wmlscript", compressible = false, binary = false, fileExtensions = List("wmls"))

    lazy val vnddotzoodotkcl: MediaType =
      MediaType("text", "vnd.zoo.kcl", compressible = false, binary = false)

    lazy val vtt: MediaType =
      MediaType("text", "vtt", compressible = true, binary = false, fileExtensions = List("vtt"))

    lazy val wgsl: MediaType =
      MediaType("text", "wgsl", compressible = false, binary = false, fileExtensions = List("wgsl"))

    lazy val xAsm: MediaType =
      MediaType("text", "x-asm", compressible = false, binary = false, fileExtensions = List("s", "asm"))

    lazy val xC: MediaType =
      MediaType(
        "text",
        "x-c",
        compressible = false,
        binary = false,
        fileExtensions = List("c", "cc", "cxx", "cpp", "h", "hh", "dic")
      )

    lazy val xComponent: MediaType =
      MediaType("text", "x-component", compressible = true, binary = false, fileExtensions = List("htc"))

    lazy val xFortran: MediaType =
      MediaType(
        "text",
        "x-fortran",
        compressible = false,
        binary = false,
        fileExtensions = List("f", "for", "f77", "f90")
      )

    lazy val xGwtRpc: MediaType =
      MediaType("text", "x-gwt-rpc", compressible = true, binary = false)

    lazy val xHandlebarsTemplate: MediaType =
      MediaType("text", "x-handlebars-template", compressible = false, binary = false, fileExtensions = List("hbs"))

    lazy val xJavaSource: MediaType =
      MediaType("text", "x-java-source", compressible = false, binary = false, fileExtensions = List("java"))

    lazy val xJqueryTmpl: MediaType =
      MediaType("text", "x-jquery-tmpl", compressible = true, binary = false)

    lazy val xLua: MediaType =
      MediaType("text", "x-lua", compressible = false, binary = false, fileExtensions = List("lua"))

    lazy val xMarkdown: MediaType =
      MediaType("text", "x-markdown", compressible = true, binary = false, fileExtensions = List("mkd"))

    lazy val xNfo: MediaType =
      MediaType("text", "x-nfo", compressible = false, binary = false, fileExtensions = List("nfo"))

    lazy val xOpml: MediaType =
      MediaType("text", "x-opml", compressible = false, binary = false, fileExtensions = List("opml"))

    lazy val xOrg: MediaType =
      MediaType("text", "x-org", compressible = true, binary = false, fileExtensions = List("org"))

    lazy val xPascal: MediaType =
      MediaType("text", "x-pascal", compressible = false, binary = false, fileExtensions = List("p", "pas"))

    lazy val xPhp: MediaType =
      MediaType("text", "x-php", compressible = true, binary = false, fileExtensions = List("php"))

    lazy val xProcessing: MediaType =
      MediaType("text", "x-processing", compressible = true, binary = false, fileExtensions = List("pde"))

    lazy val xSass: MediaType =
      MediaType("text", "x-sass", compressible = false, binary = false, fileExtensions = List("sass"))

    lazy val xScss: MediaType =
      MediaType("text", "x-scss", compressible = false, binary = false, fileExtensions = List("scss"))

    lazy val xSetext: MediaType =
      MediaType("text", "x-setext", compressible = false, binary = false, fileExtensions = List("etx"))

    lazy val xSfv: MediaType =
      MediaType("text", "x-sfv", compressible = false, binary = false, fileExtensions = List("sfv"))

    lazy val xSuseYmp: MediaType =
      MediaType("text", "x-suse-ymp", compressible = true, binary = false, fileExtensions = List("ymp"))

    lazy val xUuencode: MediaType =
      MediaType("text", "x-uuencode", compressible = false, binary = false, fileExtensions = List("uu"))

    lazy val xVcalendar: MediaType =
      MediaType("text", "x-vcalendar", compressible = false, binary = false, fileExtensions = List("vcs"))

    lazy val xVcard: MediaType =
      MediaType("text", "x-vcard", compressible = false, binary = false, fileExtensions = List("vcf"))

    lazy val xml: MediaType =
      MediaType("text", "xml", compressible = true, binary = false, fileExtensions = List("xml"))

    lazy val xmlExternalParsedEntity: MediaType =
      MediaType("text", "xml-external-parsed-entity", compressible = false, binary = false)

    lazy val yaml: MediaType =
      MediaType("text", "yaml", compressible = true, binary = false, fileExtensions = List("yaml", "yml"))

    def all: List[MediaType] = List(
      _1dInterleavedParityfec,
      cacheManifest,
      calendar,
      cmd,
      coffeescript,
      cql,
      cqlExpression,
      cqlIdentifier,
      css,
      csv,
      csvSchema,
      directory,
      dns,
      ecmascript,
      encaprtp,
      enriched,
      fhirpath,
      flexfec,
      fwdred,
      gff3,
      grammarRefList,
      hl7v2,
      html,
      jade,
      javascript,
      jcrCnd,
      jsx,
      less,
      markdown,
      mathml,
      mdx,
      mizar,
      n3,
      org,
      parameters,
      parityfec,
      plain,
      provenanceNotation,
      prsdotfallensteindotrst,
      prsdotlinesdottag,
      prsdotpropdotlogic,
      prsdottexi,
      raptorfec,
      red,
      rfc822Headers,
      richtext,
      rtf,
      rtpEncAescm128,
      rtploopback,
      rtx,
      sgml,
      shaclc,
      shex,
      slim,
      spdx,
      strings,
      stylus,
      t140,
      tabSeparatedValues,
      troff,
      turtle,
      ulpfec,
      uriList,
      vcard,
      vnddota,
      vnddotabc,
      vnddotasciiArt,
      vnddotcurl,
      vnddotcurldotdcurl,
      vnddotcurldotmcurl,
      vnddotcurldotscurl,
      vnddotdebiandotcopyright,
      vnddotdmclientscript,
      vnddotdvbdotsubtitle,
      vnddotesmertecdotthemeDescriptor,
      vnddotexchangeable,
      vnddotfamilysearchdotgedcom,
      vnddotficlabdotflt,
      vnddotfly,
      vnddotfmidotflexstor,
      vnddotgml,
      vnddotgraphviz,
      vnddothans,
      vnddothgl,
      vnddotin3ddot3dml,
      vnddotin3ddotspot,
      vnddotiptcdotnewsml,
      vnddotiptcdotnitf,
      vnddotlatexZ,
      vnddotmotoroladotreflex,
      vnddotmsMediapackage,
      vnddotnet2phonedotcommcenterdotcommand,
      vnddotradisysdotmsmlBasicLayout,
      vnddotsenxdotwarpscript,
      vnddotsidoturicatalogue,
      vnddotsosi,
      vnddotsundotj2medotappDescriptor,
      vnddottrolltechdotlinguist,
      vnddottypst,
      vnddotvcf,
      vnddotwapdotsi,
      vnddotwapdotsl,
      vnddotwapdotwml,
      vnddotwapdotwmlscript,
      vnddotzoodotkcl,
      vtt,
      wgsl,
      xAsm,
      xC,
      xComponent,
      xFortran,
      xGwtRpc,
      xHandlebarsTemplate,
      xJavaSource,
      xJqueryTmpl,
      xLua,
      xMarkdown,
      xNfo,
      xOpml,
      xOrg,
      xPascal,
      xPhp,
      xProcessing,
      xSass,
      xScss,
      xSetext,
      xSfv,
      xSuseYmp,
      xUuencode,
      xVcalendar,
      xVcard,
      xml,
      xmlExternalParsedEntity,
      yaml
    )
  }

  object video {
    lazy val _1dInterleavedParityfec: MediaType =
      MediaType("video", "1d-interleaved-parityfec", compressible = false, binary = true)

    lazy val _3gpp: MediaType =
      MediaType("video", "3gpp", compressible = false, binary = true, fileExtensions = List("3gp", "3gpp"))

    lazy val _3gppTt: MediaType =
      MediaType("video", "3gpp-tt", compressible = false, binary = true)

    lazy val _3gpp2: MediaType =
      MediaType("video", "3gpp2", compressible = false, binary = true, fileExtensions = List("3g2"))

    lazy val av1: MediaType =
      MediaType("video", "av1", compressible = false, binary = true)

    lazy val bmpeg: MediaType =
      MediaType("video", "bmpeg", compressible = false, binary = true)

    lazy val bt656: MediaType =
      MediaType("video", "bt656", compressible = false, binary = true)

    lazy val celb: MediaType =
      MediaType("video", "celb", compressible = false, binary = true)

    lazy val dv: MediaType =
      MediaType("video", "dv", compressible = false, binary = true)

    lazy val encaprtp: MediaType =
      MediaType("video", "encaprtp", compressible = false, binary = true)

    lazy val evc: MediaType =
      MediaType("video", "evc", compressible = false, binary = true)

    lazy val ffv1: MediaType =
      MediaType("video", "ffv1", compressible = false, binary = true)

    lazy val flexfec: MediaType =
      MediaType("video", "flexfec", compressible = false, binary = true)

    lazy val h261: MediaType =
      MediaType("video", "h261", compressible = false, binary = true, fileExtensions = List("h261"))

    lazy val h263: MediaType =
      MediaType("video", "h263", compressible = false, binary = true, fileExtensions = List("h263"))

    lazy val h2631998: MediaType =
      MediaType("video", "h263-1998", compressible = false, binary = true)

    lazy val h2632000: MediaType =
      MediaType("video", "h263-2000", compressible = false, binary = true)

    lazy val h264: MediaType =
      MediaType("video", "h264", compressible = false, binary = true, fileExtensions = List("h264"))

    lazy val h264Rcdo: MediaType =
      MediaType("video", "h264-rcdo", compressible = false, binary = true)

    lazy val h264Svc: MediaType =
      MediaType("video", "h264-svc", compressible = false, binary = true)

    lazy val h265: MediaType =
      MediaType("video", "h265", compressible = false, binary = true)

    lazy val h266: MediaType =
      MediaType("video", "h266", compressible = false, binary = true)

    lazy val isodotsegment: MediaType =
      MediaType("video", "iso.segment", compressible = false, binary = true, fileExtensions = List("m4s"))

    lazy val jpeg: MediaType =
      MediaType("video", "jpeg", compressible = false, binary = true, fileExtensions = List("jpgv"))

    lazy val jpeg2000: MediaType =
      MediaType("video", "jpeg2000", compressible = false, binary = true)

    lazy val jpeg2000Scl: MediaType =
      MediaType("video", "jpeg2000-scl", compressible = false, binary = true)

    lazy val jpm: MediaType =
      MediaType("video", "jpm", compressible = false, binary = true, fileExtensions = List("jpm", "jpgm"))

    lazy val jxsv: MediaType =
      MediaType("video", "jxsv", compressible = false, binary = true)

    lazy val lottieplusjson: MediaType =
      MediaType("video", "lottie+json", compressible = true, binary = true)

    lazy val matroska: MediaType =
      MediaType("video", "matroska", compressible = false, binary = true, fileExtensions = List("mkv"))

    lazy val matroska3d: MediaType =
      MediaType("video", "matroska-3d", compressible = false, binary = true, fileExtensions = List("mk3d"))

    lazy val mj2: MediaType =
      MediaType("video", "mj2", compressible = false, binary = true, fileExtensions = List("mj2", "mjp2"))

    lazy val mp1s: MediaType =
      MediaType("video", "mp1s", compressible = false, binary = true)

    lazy val mp2p: MediaType =
      MediaType("video", "mp2p", compressible = false, binary = true)

    lazy val mp2t: MediaType =
      MediaType("video", "mp2t", compressible = false, binary = true, fileExtensions = List("ts", "m2t", "m2ts", "mts"))

    lazy val mp4: MediaType =
      MediaType("video", "mp4", compressible = false, binary = true, fileExtensions = List("mp4", "mp4v", "mpg4"))

    lazy val mp4vEs: MediaType =
      MediaType("video", "mp4v-es", compressible = false, binary = true)

    lazy val mpeg: MediaType =
      MediaType(
        "video",
        "mpeg",
        compressible = false,
        binary = true,
        fileExtensions = List("mpeg", "mpg", "mpe", "m1v", "m2v")
      )

    lazy val mpeg4Generic: MediaType =
      MediaType("video", "mpeg4-generic", compressible = false, binary = true)

    lazy val mpv: MediaType =
      MediaType("video", "mpv", compressible = false, binary = true)

    lazy val nv: MediaType =
      MediaType("video", "nv", compressible = false, binary = true)

    lazy val ogg: MediaType =
      MediaType("video", "ogg", compressible = false, binary = true, fileExtensions = List("ogv"))

    lazy val parityfec: MediaType =
      MediaType("video", "parityfec", compressible = false, binary = true)

    lazy val pointer: MediaType =
      MediaType("video", "pointer", compressible = false, binary = true)

    lazy val quicktime: MediaType =
      MediaType("video", "quicktime", compressible = false, binary = true, fileExtensions = List("qt", "mov"))

    lazy val raptorfec: MediaType =
      MediaType("video", "raptorfec", compressible = false, binary = true)

    lazy val raw: MediaType =
      MediaType("video", "raw", compressible = false, binary = true)

    lazy val rtpEncAescm128: MediaType =
      MediaType("video", "rtp-enc-aescm128", compressible = false, binary = true)

    lazy val rtploopback: MediaType =
      MediaType("video", "rtploopback", compressible = false, binary = true)

    lazy val rtx: MediaType =
      MediaType("video", "rtx", compressible = false, binary = true)

    lazy val scip: MediaType =
      MediaType("video", "scip", compressible = false, binary = true)

    lazy val smpte291: MediaType =
      MediaType("video", "smpte291", compressible = false, binary = true)

    lazy val smpte292m: MediaType =
      MediaType("video", "smpte292m", compressible = false, binary = true)

    lazy val ulpfec: MediaType =
      MediaType("video", "ulpfec", compressible = false, binary = true)

    lazy val vc1: MediaType =
      MediaType("video", "vc1", compressible = false, binary = true)

    lazy val vc2: MediaType =
      MediaType("video", "vc2", compressible = false, binary = true)

    lazy val vnddotblockfactdotfactv: MediaType =
      MediaType("video", "vnd.blockfact.factv", compressible = false, binary = true)

    lazy val vnddotcctv: MediaType =
      MediaType("video", "vnd.cctv", compressible = false, binary = true)

    lazy val vnddotdecedothd: MediaType =
      MediaType("video", "vnd.dece.hd", compressible = false, binary = true, fileExtensions = List("uvh", "uvvh"))

    lazy val vnddotdecedotmobile: MediaType =
      MediaType("video", "vnd.dece.mobile", compressible = false, binary = true, fileExtensions = List("uvm", "uvvm"))

    lazy val vnddotdecedotmp4: MediaType =
      MediaType("video", "vnd.dece.mp4", compressible = false, binary = true)

    lazy val vnddotdecedotpd: MediaType =
      MediaType("video", "vnd.dece.pd", compressible = false, binary = true, fileExtensions = List("uvp", "uvvp"))

    lazy val vnddotdecedotsd: MediaType =
      MediaType("video", "vnd.dece.sd", compressible = false, binary = true, fileExtensions = List("uvs", "uvvs"))

    lazy val vnddotdecedotvideo: MediaType =
      MediaType("video", "vnd.dece.video", compressible = false, binary = true, fileExtensions = List("uvv", "uvvv"))

    lazy val vnddotdirectvdotmpeg: MediaType =
      MediaType("video", "vnd.directv.mpeg", compressible = false, binary = true)

    lazy val vnddotdirectvdotmpegTts: MediaType =
      MediaType("video", "vnd.directv.mpeg-tts", compressible = false, binary = true)

    lazy val vnddotdlnadotmpegTts: MediaType =
      MediaType("video", "vnd.dlna.mpeg-tts", compressible = false, binary = true)

    lazy val vnddotdvbdotfile: MediaType =
      MediaType("video", "vnd.dvb.file", compressible = false, binary = true, fileExtensions = List("dvb"))

    lazy val vnddotfvt: MediaType =
      MediaType("video", "vnd.fvt", compressible = false, binary = true, fileExtensions = List("fvt"))

    lazy val vnddothnsdotvideo: MediaType =
      MediaType("video", "vnd.hns.video", compressible = false, binary = true)

    lazy val vnddotiptvforumdot1dparityfec1010: MediaType =
      MediaType("video", "vnd.iptvforum.1dparityfec-1010", compressible = false, binary = true)

    lazy val vnddotiptvforumdot1dparityfec2005: MediaType =
      MediaType("video", "vnd.iptvforum.1dparityfec-2005", compressible = false, binary = true)

    lazy val vnddotiptvforumdot2dparityfec1010: MediaType =
      MediaType("video", "vnd.iptvforum.2dparityfec-1010", compressible = false, binary = true)

    lazy val vnddotiptvforumdot2dparityfec2005: MediaType =
      MediaType("video", "vnd.iptvforum.2dparityfec-2005", compressible = false, binary = true)

    lazy val vnddotiptvforumdotttsavc: MediaType =
      MediaType("video", "vnd.iptvforum.ttsavc", compressible = false, binary = true)

    lazy val vnddotiptvforumdotttsmpeg2: MediaType =
      MediaType("video", "vnd.iptvforum.ttsmpeg2", compressible = false, binary = true)

    lazy val vnddotmotoroladotvideo: MediaType =
      MediaType("video", "vnd.motorola.video", compressible = false, binary = true)

    lazy val vnddotmotoroladotvideop: MediaType =
      MediaType("video", "vnd.motorola.videop", compressible = false, binary = true)

    lazy val vnddotmpegurl: MediaType =
      MediaType("video", "vnd.mpegurl", compressible = false, binary = true, fileExtensions = List("mxu", "m4u"))

    lazy val vnddotmsPlayreadydotmediadotpyv: MediaType =
      MediaType(
        "video",
        "vnd.ms-playready.media.pyv",
        compressible = false,
        binary = true,
        fileExtensions = List("pyv")
      )

    lazy val vnddotnokiadotinterleavedMultimedia: MediaType =
      MediaType("video", "vnd.nokia.interleaved-multimedia", compressible = false, binary = true)

    lazy val vnddotnokiadotmp4vr: MediaType =
      MediaType("video", "vnd.nokia.mp4vr", compressible = false, binary = true)

    lazy val vnddotnokiadotvideovoip: MediaType =
      MediaType("video", "vnd.nokia.videovoip", compressible = false, binary = true)

    lazy val vnddotobjectvideo: MediaType =
      MediaType("video", "vnd.objectvideo", compressible = false, binary = true)

    lazy val vnddotplanar: MediaType =
      MediaType("video", "vnd.planar", compressible = false, binary = true)

    lazy val vnddotradgamettoolsdotbink: MediaType =
      MediaType("video", "vnd.radgamettools.bink", compressible = false, binary = true)

    lazy val vnddotradgamettoolsdotsmacker: MediaType =
      MediaType("video", "vnd.radgamettools.smacker", compressible = false, binary = true)

    lazy val vnddotsealeddotmpeg1: MediaType =
      MediaType("video", "vnd.sealed.mpeg1", compressible = false, binary = true)

    lazy val vnddotsealeddotmpeg4: MediaType =
      MediaType("video", "vnd.sealed.mpeg4", compressible = false, binary = true)

    lazy val vnddotsealeddotswf: MediaType =
      MediaType("video", "vnd.sealed.swf", compressible = false, binary = true)

    lazy val vnddotsealedmediadotsoftsealdotmov: MediaType =
      MediaType("video", "vnd.sealedmedia.softseal.mov", compressible = false, binary = true)

    lazy val vnddotuvvudotmp4: MediaType =
      MediaType("video", "vnd.uvvu.mp4", compressible = false, binary = true, fileExtensions = List("uvu", "uvvu"))

    lazy val vnddotvivo: MediaType =
      MediaType("video", "vnd.vivo", compressible = false, binary = true, fileExtensions = List("viv"))

    lazy val vnddotyoutubedotyt: MediaType =
      MediaType("video", "vnd.youtube.yt", compressible = false, binary = true)

    lazy val vp8: MediaType =
      MediaType("video", "vp8", compressible = false, binary = true)

    lazy val vp9: MediaType =
      MediaType("video", "vp9", compressible = false, binary = true)

    lazy val webm: MediaType =
      MediaType("video", "webm", compressible = false, binary = true, fileExtensions = List("webm"))

    lazy val xF4v: MediaType =
      MediaType("video", "x-f4v", compressible = false, binary = true, fileExtensions = List("f4v"))

    lazy val xFli: MediaType =
      MediaType("video", "x-fli", compressible = false, binary = true, fileExtensions = List("fli"))

    lazy val xFlv: MediaType =
      MediaType("video", "x-flv", compressible = false, binary = true, fileExtensions = List("flv"))

    lazy val xM4v: MediaType =
      MediaType("video", "x-m4v", compressible = false, binary = true, fileExtensions = List("m4v"))

    lazy val xMatroska: MediaType =
      MediaType("video", "x-matroska", compressible = false, binary = true, fileExtensions = List("mkv", "mk3d", "mks"))

    lazy val xMng: MediaType =
      MediaType("video", "x-mng", compressible = false, binary = true, fileExtensions = List("mng"))

    lazy val xMsAsf: MediaType =
      MediaType("video", "x-ms-asf", compressible = false, binary = true, fileExtensions = List("asf", "asx"))

    lazy val xMsVob: MediaType =
      MediaType("video", "x-ms-vob", compressible = false, binary = true, fileExtensions = List("vob"))

    lazy val xMsWm: MediaType =
      MediaType("video", "x-ms-wm", compressible = false, binary = true, fileExtensions = List("wm"))

    lazy val xMsWmv: MediaType =
      MediaType("video", "x-ms-wmv", compressible = false, binary = true, fileExtensions = List("wmv"))

    lazy val xMsWmx: MediaType =
      MediaType("video", "x-ms-wmx", compressible = false, binary = true, fileExtensions = List("wmx"))

    lazy val xMsWvx: MediaType =
      MediaType("video", "x-ms-wvx", compressible = false, binary = true, fileExtensions = List("wvx"))

    lazy val xMsvideo: MediaType =
      MediaType("video", "x-msvideo", compressible = false, binary = true, fileExtensions = List("avi"))

    lazy val xSgiMovie: MediaType =
      MediaType("video", "x-sgi-movie", compressible = false, binary = true, fileExtensions = List("movie"))

    lazy val xSmv: MediaType =
      MediaType("video", "x-smv", compressible = false, binary = true, fileExtensions = List("smv"))

    def all: List[MediaType] = List(
      _1dInterleavedParityfec,
      _3gpp,
      _3gppTt,
      _3gpp2,
      av1,
      bmpeg,
      bt656,
      celb,
      dv,
      encaprtp,
      evc,
      ffv1,
      flexfec,
      h261,
      h263,
      h2631998,
      h2632000,
      h264,
      h264Rcdo,
      h264Svc,
      h265,
      h266,
      isodotsegment,
      jpeg,
      jpeg2000,
      jpeg2000Scl,
      jpm,
      jxsv,
      lottieplusjson,
      matroska,
      matroska3d,
      mj2,
      mp1s,
      mp2p,
      mp2t,
      mp4,
      mp4vEs,
      mpeg,
      mpeg4Generic,
      mpv,
      nv,
      ogg,
      parityfec,
      pointer,
      quicktime,
      raptorfec,
      raw,
      rtpEncAescm128,
      rtploopback,
      rtx,
      scip,
      smpte291,
      smpte292m,
      ulpfec,
      vc1,
      vc2,
      vnddotblockfactdotfactv,
      vnddotcctv,
      vnddotdecedothd,
      vnddotdecedotmobile,
      vnddotdecedotmp4,
      vnddotdecedotpd,
      vnddotdecedotsd,
      vnddotdecedotvideo,
      vnddotdirectvdotmpeg,
      vnddotdirectvdotmpegTts,
      vnddotdlnadotmpegTts,
      vnddotdvbdotfile,
      vnddotfvt,
      vnddothnsdotvideo,
      vnddotiptvforumdot1dparityfec1010,
      vnddotiptvforumdot1dparityfec2005,
      vnddotiptvforumdot2dparityfec1010,
      vnddotiptvforumdot2dparityfec2005,
      vnddotiptvforumdotttsavc,
      vnddotiptvforumdotttsmpeg2,
      vnddotmotoroladotvideo,
      vnddotmotoroladotvideop,
      vnddotmpegurl,
      vnddotmsPlayreadydotmediadotpyv,
      vnddotnokiadotinterleavedMultimedia,
      vnddotnokiadotmp4vr,
      vnddotnokiadotvideovoip,
      vnddotobjectvideo,
      vnddotplanar,
      vnddotradgamettoolsdotbink,
      vnddotradgamettoolsdotsmacker,
      vnddotsealeddotmpeg1,
      vnddotsealeddotmpeg4,
      vnddotsealeddotswf,
      vnddotsealedmediadotsoftsealdotmov,
      vnddotuvvudotmp4,
      vnddotvivo,
      vnddotyoutubedotyt,
      vp8,
      vp9,
      webm,
      xF4v,
      xFli,
      xFlv,
      xM4v,
      xMatroska,
      xMng,
      xMsAsf,
      xMsVob,
      xMsWm,
      xMsWmv,
      xMsWmx,
      xMsWvx,
      xMsvideo,
      xSgiMovie,
      xSmv
    )
  }

  object x_conference {
    lazy val xCooltalk: MediaType =
      MediaType("x-conference", "x-cooltalk", compressible = false, binary = true, fileExtensions = List("ice"))

    def all: List[MediaType] = List(
      xCooltalk
    )
  }

  object x_shader {
    lazy val xFragment: MediaType =
      MediaType("x-shader", "x-fragment", compressible = true, binary = false)

    lazy val xVertex: MediaType =
      MediaType("x-shader", "x-vertex", compressible = true, binary = false)

    def all: List[MediaType] = List(
      xFragment,
      xVertex
    )
  }

  def allMediaTypes: List[MediaType] =
    application.all ++ audio.all ++ chemical.all ++ font.all ++ image.all ++ message.all ++ model.all ++ multipart.all ++ text.all ++ video.all ++ x_conference.all ++ x_shader.all
}
