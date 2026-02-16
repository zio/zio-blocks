package zio.blocks.mediatype

// AUTO-GENERATED - DO NOT EDIT
// Generated from https://github.com/jshttp/mime-db
// Run: sbt generateMediaTypes

object MediaTypes {
  import zio.blocks.mediatype.MediaType

  lazy val any: MediaType = MediaType("*", "*")

  object application {
    lazy val `1d-interleaved-parityfec`: MediaType =
      MediaType("application", "1d-interleaved-parityfec", compressible = false, binary = true)

    lazy val `3gpdash-qoe-report+xml`: MediaType =
      MediaType("application", "3gpdash-qoe-report+xml", compressible = true, binary = true)

    lazy val `3gpp-ims+xml`: MediaType =
      MediaType("application", "3gpp-ims+xml", compressible = true, binary = true)

    lazy val `3gpp-mbs-object-manifest+json`: MediaType =
      MediaType("application", "3gpp-mbs-object-manifest+json", compressible = true, binary = false)

    lazy val `3gpp-mbs-user-service-descriptions+json`: MediaType =
      MediaType("application", "3gpp-mbs-user-service-descriptions+json", compressible = true, binary = false)

    lazy val `3gpp-media-delivery-metrics-report+json`: MediaType =
      MediaType("application", "3gpp-media-delivery-metrics-report+json", compressible = true, binary = false)

    lazy val `3gpphal+json`: MediaType =
      MediaType("application", "3gpphal+json", compressible = true, binary = false)

    lazy val `3gpphalforms+json`: MediaType =
      MediaType("application", "3gpphalforms+json", compressible = true, binary = false)

    lazy val `a2l`: MediaType =
      MediaType("application", "a2l", compressible = false, binary = true)

    lazy val `ace+cbor`: MediaType =
      MediaType("application", "ace+cbor", compressible = false, binary = true)

    lazy val `ace+json`: MediaType =
      MediaType("application", "ace+json", compressible = true, binary = false)

    lazy val `ace-groupcomm+cbor`: MediaType =
      MediaType("application", "ace-groupcomm+cbor", compressible = false, binary = true)

    lazy val `ace-trl+cbor`: MediaType =
      MediaType("application", "ace-trl+cbor", compressible = false, binary = true)

    lazy val `activemessage`: MediaType =
      MediaType("application", "activemessage", compressible = false, binary = true)

    lazy val `activity+json`: MediaType =
      MediaType("application", "activity+json", compressible = true, binary = false)

    lazy val `aif+cbor`: MediaType =
      MediaType("application", "aif+cbor", compressible = false, binary = true)

    lazy val `aif+json`: MediaType =
      MediaType("application", "aif+json", compressible = true, binary = false)

    lazy val `alto-cdni+json`: MediaType =
      MediaType("application", "alto-cdni+json", compressible = true, binary = false)

    lazy val `alto-cdnifilter+json`: MediaType =
      MediaType("application", "alto-cdnifilter+json", compressible = true, binary = false)

    lazy val `alto-costmap+json`: MediaType =
      MediaType("application", "alto-costmap+json", compressible = true, binary = false)

    lazy val `alto-costmapfilter+json`: MediaType =
      MediaType("application", "alto-costmapfilter+json", compressible = true, binary = false)

    lazy val `alto-directory+json`: MediaType =
      MediaType("application", "alto-directory+json", compressible = true, binary = false)

    lazy val `alto-endpointcost+json`: MediaType =
      MediaType("application", "alto-endpointcost+json", compressible = true, binary = false)

    lazy val `alto-endpointcostparams+json`: MediaType =
      MediaType("application", "alto-endpointcostparams+json", compressible = true, binary = false)

    lazy val `alto-endpointprop+json`: MediaType =
      MediaType("application", "alto-endpointprop+json", compressible = true, binary = false)

    lazy val `alto-endpointpropparams+json`: MediaType =
      MediaType("application", "alto-endpointpropparams+json", compressible = true, binary = false)

    lazy val `alto-error+json`: MediaType =
      MediaType("application", "alto-error+json", compressible = true, binary = false)

    lazy val `alto-networkmap+json`: MediaType =
      MediaType("application", "alto-networkmap+json", compressible = true, binary = false)

    lazy val `alto-networkmapfilter+json`: MediaType =
      MediaType("application", "alto-networkmapfilter+json", compressible = true, binary = false)

    lazy val `alto-propmap+json`: MediaType =
      MediaType("application", "alto-propmap+json", compressible = true, binary = false)

    lazy val `alto-propmapparams+json`: MediaType =
      MediaType("application", "alto-propmapparams+json", compressible = true, binary = false)

    lazy val `alto-tips+json`: MediaType =
      MediaType("application", "alto-tips+json", compressible = true, binary = false)

    lazy val `alto-tipsparams+json`: MediaType =
      MediaType("application", "alto-tipsparams+json", compressible = true, binary = false)

    lazy val `alto-updatestreamcontrol+json`: MediaType =
      MediaType("application", "alto-updatestreamcontrol+json", compressible = true, binary = false)

    lazy val `alto-updatestreamparams+json`: MediaType =
      MediaType("application", "alto-updatestreamparams+json", compressible = true, binary = false)

    lazy val `aml`: MediaType =
      MediaType("application", "aml", compressible = false, binary = true)

    lazy val `andrew-inset`: MediaType =
      MediaType("application", "andrew-inset", compressible = false, binary = true, fileExtensions = List("ez"))

    lazy val `appinstaller`: MediaType =
      MediaType(
        "application",
        "appinstaller",
        compressible = false,
        binary = true,
        fileExtensions = List("appinstaller")
      )

    lazy val `applefile`: MediaType =
      MediaType("application", "applefile", compressible = false, binary = true)

    lazy val `applixware`: MediaType =
      MediaType("application", "applixware", compressible = false, binary = true, fileExtensions = List("aw"))

    lazy val `appx`: MediaType =
      MediaType("application", "appx", compressible = false, binary = true, fileExtensions = List("appx"))

    lazy val `appxbundle`: MediaType =
      MediaType("application", "appxbundle", compressible = false, binary = true, fileExtensions = List("appxbundle"))

    lazy val `asyncapi+json`: MediaType =
      MediaType("application", "asyncapi+json", compressible = true, binary = false)

    lazy val `asyncapi+yaml`: MediaType =
      MediaType("application", "asyncapi+yaml", compressible = false, binary = true)

    lazy val `at+jwt`: MediaType =
      MediaType("application", "at+jwt", compressible = false, binary = true)

    lazy val `atf`: MediaType =
      MediaType("application", "atf", compressible = false, binary = true)

    lazy val `atfx`: MediaType =
      MediaType("application", "atfx", compressible = false, binary = true)

    lazy val `atom+xml`: MediaType =
      MediaType("application", "atom+xml", compressible = true, binary = true, fileExtensions = List("atom"))

    lazy val `atomcat+xml`: MediaType =
      MediaType("application", "atomcat+xml", compressible = true, binary = true, fileExtensions = List("atomcat"))

    lazy val `atomdeleted+xml`: MediaType =
      MediaType(
        "application",
        "atomdeleted+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("atomdeleted")
      )

    lazy val `atomicmail`: MediaType =
      MediaType("application", "atomicmail", compressible = false, binary = true)

    lazy val `atomsvc+xml`: MediaType =
      MediaType("application", "atomsvc+xml", compressible = true, binary = true, fileExtensions = List("atomsvc"))

    lazy val `atsc-dwd+xml`: MediaType =
      MediaType("application", "atsc-dwd+xml", compressible = true, binary = true, fileExtensions = List("dwd"))

    lazy val `atsc-dynamic-event-message`: MediaType =
      MediaType("application", "atsc-dynamic-event-message", compressible = false, binary = true)

    lazy val `atsc-held+xml`: MediaType =
      MediaType("application", "atsc-held+xml", compressible = true, binary = true, fileExtensions = List("held"))

    lazy val `atsc-rdt+json`: MediaType =
      MediaType("application", "atsc-rdt+json", compressible = true, binary = false)

    lazy val `atsc-rsat+xml`: MediaType =
      MediaType("application", "atsc-rsat+xml", compressible = true, binary = true, fileExtensions = List("rsat"))

    lazy val `atxml`: MediaType =
      MediaType("application", "atxml", compressible = false, binary = true)

    lazy val `auth-policy+xml`: MediaType =
      MediaType("application", "auth-policy+xml", compressible = true, binary = true)

    lazy val `automationml-aml+xml`: MediaType =
      MediaType("application", "automationml-aml+xml", compressible = true, binary = true, fileExtensions = List("aml"))

    lazy val `automationml-amlx+zip`: MediaType =
      MediaType(
        "application",
        "automationml-amlx+zip",
        compressible = false,
        binary = true,
        fileExtensions = List("amlx")
      )

    lazy val `bacnet-xdd+zip`: MediaType =
      MediaType("application", "bacnet-xdd+zip", compressible = false, binary = true)

    lazy val `batch-smtp`: MediaType =
      MediaType("application", "batch-smtp", compressible = false, binary = true)

    lazy val `bdoc`: MediaType =
      MediaType("application", "bdoc", compressible = false, binary = true, fileExtensions = List("bdoc"))

    lazy val `beep+xml`: MediaType =
      MediaType("application", "beep+xml", compressible = true, binary = true)

    lazy val `bufr`: MediaType =
      MediaType("application", "bufr", compressible = false, binary = true)

    lazy val `c2pa`: MediaType =
      MediaType("application", "c2pa", compressible = false, binary = true)

    lazy val `calendar+json`: MediaType =
      MediaType("application", "calendar+json", compressible = true, binary = false)

    lazy val `calendar+xml`: MediaType =
      MediaType("application", "calendar+xml", compressible = true, binary = true, fileExtensions = List("xcs"))

    lazy val `call-completion`: MediaType =
      MediaType("application", "call-completion", compressible = false, binary = true)

    lazy val `cals-1840`: MediaType =
      MediaType("application", "cals-1840", compressible = false, binary = true)

    lazy val `captive+json`: MediaType =
      MediaType("application", "captive+json", compressible = true, binary = false)

    lazy val `cbor`: MediaType =
      MediaType("application", "cbor", compressible = false, binary = true)

    lazy val `cbor-seq`: MediaType =
      MediaType("application", "cbor-seq", compressible = false, binary = true)

    lazy val `cccex`: MediaType =
      MediaType("application", "cccex", compressible = false, binary = true)

    lazy val `ccmp+xml`: MediaType =
      MediaType("application", "ccmp+xml", compressible = true, binary = true)

    lazy val `ccxml+xml`: MediaType =
      MediaType("application", "ccxml+xml", compressible = true, binary = true, fileExtensions = List("ccxml"))

    lazy val `cda+xml`: MediaType =
      MediaType("application", "cda+xml", compressible = true, binary = true)

    lazy val `cdfx+xml`: MediaType =
      MediaType("application", "cdfx+xml", compressible = true, binary = true, fileExtensions = List("cdfx"))

    lazy val `cdmi-capability`: MediaType =
      MediaType("application", "cdmi-capability", compressible = false, binary = true, fileExtensions = List("cdmia"))

    lazy val `cdmi-container`: MediaType =
      MediaType("application", "cdmi-container", compressible = false, binary = true, fileExtensions = List("cdmic"))

    lazy val `cdmi-domain`: MediaType =
      MediaType("application", "cdmi-domain", compressible = false, binary = true, fileExtensions = List("cdmid"))

    lazy val `cdmi-object`: MediaType =
      MediaType("application", "cdmi-object", compressible = false, binary = true, fileExtensions = List("cdmio"))

    lazy val `cdmi-queue`: MediaType =
      MediaType("application", "cdmi-queue", compressible = false, binary = true, fileExtensions = List("cdmiq"))

    lazy val `cdni`: MediaType =
      MediaType("application", "cdni", compressible = false, binary = true)

    lazy val `ce+cbor`: MediaType =
      MediaType("application", "ce+cbor", compressible = false, binary = true)

    lazy val `cea`: MediaType =
      MediaType("application", "cea", compressible = false, binary = true)

    lazy val `cea-2018+xml`: MediaType =
      MediaType("application", "cea-2018+xml", compressible = true, binary = true)

    lazy val `cellml+xml`: MediaType =
      MediaType("application", "cellml+xml", compressible = true, binary = true)

    lazy val `cfw`: MediaType =
      MediaType("application", "cfw", compressible = false, binary = true)

    lazy val `cid`: MediaType =
      MediaType("application", "cid", compressible = false, binary = true)

    lazy val `cid-edhoc+cbor-seq`: MediaType =
      MediaType("application", "cid-edhoc+cbor-seq", compressible = false, binary = true)

    lazy val `city+json`: MediaType =
      MediaType("application", "city+json", compressible = true, binary = false)

    lazy val `city+json-seq`: MediaType =
      MediaType("application", "city+json-seq", compressible = false, binary = true)

    lazy val `clr`: MediaType =
      MediaType("application", "clr", compressible = false, binary = true)

    lazy val `clue+xml`: MediaType =
      MediaType("application", "clue+xml", compressible = true, binary = true)

    lazy val `clue_info+xml`: MediaType =
      MediaType("application", "clue_info+xml", compressible = true, binary = true)

    lazy val `cms`: MediaType =
      MediaType("application", "cms", compressible = false, binary = true)

    lazy val `cmw+cbor`: MediaType =
      MediaType("application", "cmw+cbor", compressible = false, binary = true)

    lazy val `cmw+cose`: MediaType =
      MediaType("application", "cmw+cose", compressible = false, binary = true)

    lazy val `cmw+json`: MediaType =
      MediaType("application", "cmw+json", compressible = true, binary = false)

    lazy val `cmw+jws`: MediaType =
      MediaType("application", "cmw+jws", compressible = false, binary = true)

    lazy val `cnrp+xml`: MediaType =
      MediaType("application", "cnrp+xml", compressible = true, binary = true)

    lazy val `coap-eap`: MediaType =
      MediaType("application", "coap-eap", compressible = false, binary = true)

    lazy val `coap-group+json`: MediaType =
      MediaType("application", "coap-group+json", compressible = true, binary = false)

    lazy val `coap-payload`: MediaType =
      MediaType("application", "coap-payload", compressible = false, binary = true)

    lazy val `commonground`: MediaType =
      MediaType("application", "commonground", compressible = false, binary = true)

    lazy val `concise-problem-details+cbor`: MediaType =
      MediaType("application", "concise-problem-details+cbor", compressible = false, binary = true)

    lazy val `conference-info+xml`: MediaType =
      MediaType("application", "conference-info+xml", compressible = true, binary = true)

    lazy val `cose`: MediaType =
      MediaType("application", "cose", compressible = false, binary = true)

    lazy val `cose-key`: MediaType =
      MediaType("application", "cose-key", compressible = false, binary = true)

    lazy val `cose-key-set`: MediaType =
      MediaType("application", "cose-key-set", compressible = false, binary = true)

    lazy val `cose-x509`: MediaType =
      MediaType("application", "cose-x509", compressible = false, binary = true)

    lazy val `cpl+xml`: MediaType =
      MediaType("application", "cpl+xml", compressible = true, binary = true, fileExtensions = List("cpl"))

    lazy val `csrattrs`: MediaType =
      MediaType("application", "csrattrs", compressible = false, binary = true)

    lazy val `csta+xml`: MediaType =
      MediaType("application", "csta+xml", compressible = true, binary = true)

    lazy val `cstadata+xml`: MediaType =
      MediaType("application", "cstadata+xml", compressible = true, binary = true)

    lazy val `csvm+json`: MediaType =
      MediaType("application", "csvm+json", compressible = true, binary = false)

    lazy val `cu-seeme`: MediaType =
      MediaType("application", "cu-seeme", compressible = false, binary = true, fileExtensions = List("cu"))

    lazy val `cwl`: MediaType =
      MediaType("application", "cwl", compressible = false, binary = true, fileExtensions = List("cwl"))

    lazy val `cwl+json`: MediaType =
      MediaType("application", "cwl+json", compressible = true, binary = false)

    lazy val `cwl+yaml`: MediaType =
      MediaType("application", "cwl+yaml", compressible = false, binary = true)

    lazy val `cwt`: MediaType =
      MediaType("application", "cwt", compressible = false, binary = true)

    lazy val `cybercash`: MediaType =
      MediaType("application", "cybercash", compressible = false, binary = true)

    lazy val `dart`: MediaType =
      MediaType("application", "dart", compressible = true, binary = true)

    lazy val `dash+xml`: MediaType =
      MediaType("application", "dash+xml", compressible = true, binary = true, fileExtensions = List("mpd"))

    lazy val `dash-patch+xml`: MediaType =
      MediaType("application", "dash-patch+xml", compressible = true, binary = true, fileExtensions = List("mpp"))

    lazy val `dashdelta`: MediaType =
      MediaType("application", "dashdelta", compressible = false, binary = true)

    lazy val `davmount+xml`: MediaType =
      MediaType("application", "davmount+xml", compressible = true, binary = true, fileExtensions = List("davmount"))

    lazy val `dca-rft`: MediaType =
      MediaType("application", "dca-rft", compressible = false, binary = true)

    lazy val `dcd`: MediaType =
      MediaType("application", "dcd", compressible = false, binary = true)

    lazy val `dec-dx`: MediaType =
      MediaType("application", "dec-dx", compressible = false, binary = true)

    lazy val `dialog-info+xml`: MediaType =
      MediaType("application", "dialog-info+xml", compressible = true, binary = true)

    lazy val `dicom`: MediaType =
      MediaType("application", "dicom", compressible = false, binary = true, fileExtensions = List("dcm"))

    lazy val `dicom+json`: MediaType =
      MediaType("application", "dicom+json", compressible = true, binary = false)

    lazy val `dicom+xml`: MediaType =
      MediaType("application", "dicom+xml", compressible = true, binary = true)

    lazy val `did`: MediaType =
      MediaType("application", "did", compressible = false, binary = true)

    lazy val `dii`: MediaType =
      MediaType("application", "dii", compressible = false, binary = true)

    lazy val `dit`: MediaType =
      MediaType("application", "dit", compressible = false, binary = true)

    lazy val `dns`: MediaType =
      MediaType("application", "dns", compressible = false, binary = true)

    lazy val `dns+json`: MediaType =
      MediaType("application", "dns+json", compressible = true, binary = false)

    lazy val `dns-message`: MediaType =
      MediaType("application", "dns-message", compressible = false, binary = true)

    lazy val `docbook+xml`: MediaType =
      MediaType("application", "docbook+xml", compressible = true, binary = true, fileExtensions = List("dbk"))

    lazy val `dots+cbor`: MediaType =
      MediaType("application", "dots+cbor", compressible = false, binary = true)

    lazy val `dpop+jwt`: MediaType =
      MediaType("application", "dpop+jwt", compressible = false, binary = true)

    lazy val `dskpp+xml`: MediaType =
      MediaType("application", "dskpp+xml", compressible = true, binary = true)

    lazy val `dssc+der`: MediaType =
      MediaType("application", "dssc+der", compressible = false, binary = true, fileExtensions = List("dssc"))

    lazy val `dssc+xml`: MediaType =
      MediaType("application", "dssc+xml", compressible = true, binary = true, fileExtensions = List("xdssc"))

    lazy val `dvcs`: MediaType =
      MediaType("application", "dvcs", compressible = false, binary = true)

    lazy val `eat+cwt`: MediaType =
      MediaType("application", "eat+cwt", compressible = false, binary = true)

    lazy val `eat+jwt`: MediaType =
      MediaType("application", "eat+jwt", compressible = false, binary = true)

    lazy val `eat-bun+cbor`: MediaType =
      MediaType("application", "eat-bun+cbor", compressible = false, binary = true)

    lazy val `eat-bun+json`: MediaType =
      MediaType("application", "eat-bun+json", compressible = true, binary = false)

    lazy val `eat-ucs+cbor`: MediaType =
      MediaType("application", "eat-ucs+cbor", compressible = false, binary = true)

    lazy val `eat-ucs+json`: MediaType =
      MediaType("application", "eat-ucs+json", compressible = true, binary = false)

    lazy val `ecmascript`: MediaType =
      MediaType("application", "ecmascript", compressible = true, binary = true, fileExtensions = List("ecma"))

    lazy val `edhoc+cbor-seq`: MediaType =
      MediaType("application", "edhoc+cbor-seq", compressible = false, binary = true)

    lazy val `edi-consent`: MediaType =
      MediaType("application", "edi-consent", compressible = false, binary = true)

    lazy val `edi-x12`: MediaType =
      MediaType("application", "edi-x12", compressible = false, binary = true)

    lazy val `edifact`: MediaType =
      MediaType("application", "edifact", compressible = false, binary = true)

    lazy val `efi`: MediaType =
      MediaType("application", "efi", compressible = false, binary = true)

    lazy val `elm+json`: MediaType =
      MediaType("application", "elm+json", compressible = true, binary = false)

    lazy val `elm+xml`: MediaType =
      MediaType("application", "elm+xml", compressible = true, binary = true)

    lazy val `emergencycalldata.cap+xml`: MediaType =
      MediaType("application", "emergencycalldata.cap+xml", compressible = true, binary = true)

    lazy val `emergencycalldata.comment+xml`: MediaType =
      MediaType("application", "emergencycalldata.comment+xml", compressible = true, binary = true)

    lazy val `emergencycalldata.control+xml`: MediaType =
      MediaType("application", "emergencycalldata.control+xml", compressible = true, binary = true)

    lazy val `emergencycalldata.deviceinfo+xml`: MediaType =
      MediaType("application", "emergencycalldata.deviceinfo+xml", compressible = true, binary = true)

    lazy val `emergencycalldata.ecall.msd`: MediaType =
      MediaType("application", "emergencycalldata.ecall.msd", compressible = false, binary = true)

    lazy val `emergencycalldata.legacyesn+json`: MediaType =
      MediaType("application", "emergencycalldata.legacyesn+json", compressible = true, binary = false)

    lazy val `emergencycalldata.providerinfo+xml`: MediaType =
      MediaType("application", "emergencycalldata.providerinfo+xml", compressible = true, binary = true)

    lazy val `emergencycalldata.serviceinfo+xml`: MediaType =
      MediaType("application", "emergencycalldata.serviceinfo+xml", compressible = true, binary = true)

    lazy val `emergencycalldata.subscriberinfo+xml`: MediaType =
      MediaType("application", "emergencycalldata.subscriberinfo+xml", compressible = true, binary = true)

    lazy val `emergencycalldata.veds+xml`: MediaType =
      MediaType("application", "emergencycalldata.veds+xml", compressible = true, binary = true)

    lazy val `emma+xml`: MediaType =
      MediaType("application", "emma+xml", compressible = true, binary = true, fileExtensions = List("emma"))

    lazy val `emotionml+xml`: MediaType =
      MediaType("application", "emotionml+xml", compressible = true, binary = true, fileExtensions = List("emotionml"))

    lazy val `encaprtp`: MediaType =
      MediaType("application", "encaprtp", compressible = false, binary = true)

    lazy val `entity-statement+jwt`: MediaType =
      MediaType("application", "entity-statement+jwt", compressible = false, binary = true)

    lazy val `epp+xml`: MediaType =
      MediaType("application", "epp+xml", compressible = true, binary = true)

    lazy val `epub+zip`: MediaType =
      MediaType("application", "epub+zip", compressible = false, binary = true, fileExtensions = List("epub"))

    lazy val `eshop`: MediaType =
      MediaType("application", "eshop", compressible = false, binary = true)

    lazy val `exi`: MediaType =
      MediaType("application", "exi", compressible = false, binary = true, fileExtensions = List("exi"))

    lazy val `expect-ct-report+json`: MediaType =
      MediaType("application", "expect-ct-report+json", compressible = true, binary = false)

    lazy val `express`: MediaType =
      MediaType("application", "express", compressible = false, binary = true, fileExtensions = List("exp"))

    lazy val `fastinfoset`: MediaType =
      MediaType("application", "fastinfoset", compressible = false, binary = true)

    lazy val `fastsoap`: MediaType =
      MediaType("application", "fastsoap", compressible = false, binary = true)

    lazy val `fdf`: MediaType =
      MediaType("application", "fdf", compressible = false, binary = true, fileExtensions = List("fdf"))

    lazy val `fdt+xml`: MediaType =
      MediaType("application", "fdt+xml", compressible = true, binary = true, fileExtensions = List("fdt"))

    lazy val `fhir+json`: MediaType =
      MediaType("application", "fhir+json", compressible = true, binary = false)

    lazy val `fhir+xml`: MediaType =
      MediaType("application", "fhir+xml", compressible = true, binary = true)

    lazy val `fido.trusted-apps+json`: MediaType =
      MediaType("application", "fido.trusted-apps+json", compressible = true, binary = false)

    lazy val `fits`: MediaType =
      MediaType("application", "fits", compressible = false, binary = true)

    lazy val `flexfec`: MediaType =
      MediaType("application", "flexfec", compressible = false, binary = true)

    lazy val `font-sfnt`: MediaType =
      MediaType("application", "font-sfnt", compressible = false, binary = true)

    lazy val `font-tdpfr`: MediaType =
      MediaType("application", "font-tdpfr", compressible = false, binary = true, fileExtensions = List("pfr"))

    lazy val `font-woff`: MediaType =
      MediaType("application", "font-woff", compressible = false, binary = true)

    lazy val `framework-attributes+xml`: MediaType =
      MediaType("application", "framework-attributes+xml", compressible = true, binary = true)

    lazy val `geo+json`: MediaType =
      MediaType("application", "geo+json", compressible = true, binary = false, fileExtensions = List("geojson"))

    lazy val `geo+json-seq`: MediaType =
      MediaType("application", "geo+json-seq", compressible = false, binary = true)

    lazy val `geofeed+csv`: MediaType =
      MediaType("application", "geofeed+csv", compressible = false, binary = true)

    lazy val `geopackage+sqlite3`: MediaType =
      MediaType("application", "geopackage+sqlite3", compressible = false, binary = true)

    lazy val `geopose+json`: MediaType =
      MediaType("application", "geopose+json", compressible = true, binary = false)

    lazy val `geoxacml+json`: MediaType =
      MediaType("application", "geoxacml+json", compressible = true, binary = false)

    lazy val `geoxacml+xml`: MediaType =
      MediaType("application", "geoxacml+xml", compressible = true, binary = true)

    lazy val `gltf-buffer`: MediaType =
      MediaType("application", "gltf-buffer", compressible = false, binary = true)

    lazy val `gml+xml`: MediaType =
      MediaType("application", "gml+xml", compressible = true, binary = true, fileExtensions = List("gml"))

    lazy val `gnap-binding-jws`: MediaType =
      MediaType("application", "gnap-binding-jws", compressible = false, binary = true)

    lazy val `gnap-binding-jwsd`: MediaType =
      MediaType("application", "gnap-binding-jwsd", compressible = false, binary = true)

    lazy val `gnap-binding-rotation-jws`: MediaType =
      MediaType("application", "gnap-binding-rotation-jws", compressible = false, binary = true)

    lazy val `gnap-binding-rotation-jwsd`: MediaType =
      MediaType("application", "gnap-binding-rotation-jwsd", compressible = false, binary = true)

    lazy val `gpx+xml`: MediaType =
      MediaType("application", "gpx+xml", compressible = true, binary = true, fileExtensions = List("gpx"))

    lazy val `grib`: MediaType =
      MediaType("application", "grib", compressible = false, binary = true)

    lazy val `gxf`: MediaType =
      MediaType("application", "gxf", compressible = false, binary = true, fileExtensions = List("gxf"))

    lazy val `gzip`: MediaType =
      MediaType("application", "gzip", compressible = false, binary = true, fileExtensions = List("gz"))

    lazy val `h224`: MediaType =
      MediaType("application", "h224", compressible = false, binary = true)

    lazy val `held+xml`: MediaType =
      MediaType("application", "held+xml", compressible = true, binary = true)

    lazy val `hjson`: MediaType =
      MediaType("application", "hjson", compressible = false, binary = false, fileExtensions = List("hjson"))

    lazy val `hl7v2+xml`: MediaType =
      MediaType("application", "hl7v2+xml", compressible = true, binary = true)

    lazy val `http`: MediaType =
      MediaType("application", "http", compressible = false, binary = true)

    lazy val `hyperstudio`: MediaType =
      MediaType("application", "hyperstudio", compressible = false, binary = true, fileExtensions = List("stk"))

    lazy val `ibe-key-request+xml`: MediaType =
      MediaType("application", "ibe-key-request+xml", compressible = true, binary = true)

    lazy val `ibe-pkg-reply+xml`: MediaType =
      MediaType("application", "ibe-pkg-reply+xml", compressible = true, binary = true)

    lazy val `ibe-pp-data`: MediaType =
      MediaType("application", "ibe-pp-data", compressible = false, binary = true)

    lazy val `iges`: MediaType =
      MediaType("application", "iges", compressible = false, binary = true)

    lazy val `im-iscomposing+xml`: MediaType =
      MediaType("application", "im-iscomposing+xml", compressible = true, binary = true)

    lazy val `index`: MediaType =
      MediaType("application", "index", compressible = false, binary = true)

    lazy val `index.cmd`: MediaType =
      MediaType("application", "index.cmd", compressible = false, binary = true)

    lazy val `index.obj`: MediaType =
      MediaType("application", "index.obj", compressible = false, binary = true)

    lazy val `index.response`: MediaType =
      MediaType("application", "index.response", compressible = false, binary = true)

    lazy val `index.vnd`: MediaType =
      MediaType("application", "index.vnd", compressible = false, binary = true)

    lazy val `inkml+xml`: MediaType =
      MediaType("application", "inkml+xml", compressible = true, binary = true, fileExtensions = List("ink", "inkml"))

    lazy val `iotp`: MediaType =
      MediaType("application", "iotp", compressible = false, binary = true)

    lazy val `ipfix`: MediaType =
      MediaType("application", "ipfix", compressible = false, binary = true, fileExtensions = List("ipfix"))

    lazy val `ipp`: MediaType =
      MediaType("application", "ipp", compressible = false, binary = true)

    lazy val `isup`: MediaType =
      MediaType("application", "isup", compressible = false, binary = true)

    lazy val `its+xml`: MediaType =
      MediaType("application", "its+xml", compressible = true, binary = true, fileExtensions = List("its"))

    lazy val `java-archive`: MediaType =
      MediaType(
        "application",
        "java-archive",
        compressible = false,
        binary = true,
        fileExtensions = List("jar", "war", "ear")
      )

    lazy val `java-serialized-object`: MediaType =
      MediaType(
        "application",
        "java-serialized-object",
        compressible = false,
        binary = true,
        fileExtensions = List("ser")
      )

    lazy val `java-vm`: MediaType =
      MediaType("application", "java-vm", compressible = false, binary = true, fileExtensions = List("class"))

    lazy val `javascript`: MediaType =
      MediaType("application", "javascript", compressible = true, binary = false, fileExtensions = List("js"))

    lazy val `jf2feed+json`: MediaType =
      MediaType("application", "jf2feed+json", compressible = true, binary = false)

    lazy val `jose`: MediaType =
      MediaType("application", "jose", compressible = false, binary = true)

    lazy val `jose+json`: MediaType =
      MediaType("application", "jose+json", compressible = true, binary = false)

    lazy val `jrd+json`: MediaType =
      MediaType("application", "jrd+json", compressible = true, binary = false)

    lazy val `jscalendar+json`: MediaType =
      MediaType("application", "jscalendar+json", compressible = true, binary = false)

    lazy val `jscontact+json`: MediaType =
      MediaType("application", "jscontact+json", compressible = true, binary = false)

    lazy val `json`: MediaType =
      MediaType("application", "json", compressible = true, binary = false, fileExtensions = List("json", "map"))

    lazy val `json-patch+json`: MediaType =
      MediaType("application", "json-patch+json", compressible = true, binary = false)

    lazy val `json-patch-query+json`: MediaType =
      MediaType("application", "json-patch-query+json", compressible = true, binary = false)

    lazy val `json-seq`: MediaType =
      MediaType("application", "json-seq", compressible = false, binary = true)

    lazy val `json5`: MediaType =
      MediaType("application", "json5", compressible = false, binary = true, fileExtensions = List("json5"))

    lazy val `jsonml+json`: MediaType =
      MediaType("application", "jsonml+json", compressible = true, binary = false, fileExtensions = List("jsonml"))

    lazy val `jsonpath`: MediaType =
      MediaType("application", "jsonpath", compressible = false, binary = true)

    lazy val `jwk+json`: MediaType =
      MediaType("application", "jwk+json", compressible = true, binary = false)

    lazy val `jwk-set+json`: MediaType =
      MediaType("application", "jwk-set+json", compressible = true, binary = false)

    lazy val `jwk-set+jwt`: MediaType =
      MediaType("application", "jwk-set+jwt", compressible = false, binary = true)

    lazy val `jwt`: MediaType =
      MediaType("application", "jwt", compressible = false, binary = true)

    lazy val `kb+jwt`: MediaType =
      MediaType("application", "kb+jwt", compressible = false, binary = true)

    lazy val `kbl+xml`: MediaType =
      MediaType("application", "kbl+xml", compressible = true, binary = true, fileExtensions = List("kbl"))

    lazy val `kpml-request+xml`: MediaType =
      MediaType("application", "kpml-request+xml", compressible = true, binary = true)

    lazy val `kpml-response+xml`: MediaType =
      MediaType("application", "kpml-response+xml", compressible = true, binary = true)

    lazy val `ld+json`: MediaType =
      MediaType("application", "ld+json", compressible = true, binary = false, fileExtensions = List("jsonld"))

    lazy val `lgr+xml`: MediaType =
      MediaType("application", "lgr+xml", compressible = true, binary = true, fileExtensions = List("lgr"))

    lazy val `link-format`: MediaType =
      MediaType("application", "link-format", compressible = false, binary = true)

    lazy val `linkset`: MediaType =
      MediaType("application", "linkset", compressible = false, binary = true)

    lazy val `linkset+json`: MediaType =
      MediaType("application", "linkset+json", compressible = true, binary = false)

    lazy val `load-control+xml`: MediaType =
      MediaType("application", "load-control+xml", compressible = true, binary = true)

    lazy val `logout+jwt`: MediaType =
      MediaType("application", "logout+jwt", compressible = false, binary = true)

    lazy val `lost+xml`: MediaType =
      MediaType("application", "lost+xml", compressible = true, binary = true, fileExtensions = List("lostxml"))

    lazy val `lostsync+xml`: MediaType =
      MediaType("application", "lostsync+xml", compressible = true, binary = true)

    lazy val `lpf+zip`: MediaType =
      MediaType("application", "lpf+zip", compressible = false, binary = true)

    lazy val `lxf`: MediaType =
      MediaType("application", "lxf", compressible = false, binary = true)

    lazy val `mac-binhex40`: MediaType =
      MediaType("application", "mac-binhex40", compressible = false, binary = true, fileExtensions = List("hqx"))

    lazy val `mac-compactpro`: MediaType =
      MediaType("application", "mac-compactpro", compressible = false, binary = true, fileExtensions = List("cpt"))

    lazy val `macwriteii`: MediaType =
      MediaType("application", "macwriteii", compressible = false, binary = true)

    lazy val `mads+xml`: MediaType =
      MediaType("application", "mads+xml", compressible = true, binary = true, fileExtensions = List("mads"))

    lazy val `manifest+json`: MediaType =
      MediaType(
        "application",
        "manifest+json",
        compressible = true,
        binary = false,
        fileExtensions = List("webmanifest")
      )

    lazy val `marc`: MediaType =
      MediaType("application", "marc", compressible = false, binary = true, fileExtensions = List("mrc"))

    lazy val `marcxml+xml`: MediaType =
      MediaType("application", "marcxml+xml", compressible = true, binary = true, fileExtensions = List("mrcx"))

    lazy val `mathematica`: MediaType =
      MediaType(
        "application",
        "mathematica",
        compressible = false,
        binary = true,
        fileExtensions = List("ma", "nb", "mb")
      )

    lazy val `mathml+xml`: MediaType =
      MediaType("application", "mathml+xml", compressible = true, binary = true, fileExtensions = List("mathml"))

    lazy val `mathml-content+xml`: MediaType =
      MediaType("application", "mathml-content+xml", compressible = true, binary = true)

    lazy val `mathml-presentation+xml`: MediaType =
      MediaType("application", "mathml-presentation+xml", compressible = true, binary = true)

    lazy val `mbms-associated-procedure-description+xml`: MediaType =
      MediaType("application", "mbms-associated-procedure-description+xml", compressible = true, binary = true)

    lazy val `mbms-deregister+xml`: MediaType =
      MediaType("application", "mbms-deregister+xml", compressible = true, binary = true)

    lazy val `mbms-envelope+xml`: MediaType =
      MediaType("application", "mbms-envelope+xml", compressible = true, binary = true)

    lazy val `mbms-msk+xml`: MediaType =
      MediaType("application", "mbms-msk+xml", compressible = true, binary = true)

    lazy val `mbms-msk-response+xml`: MediaType =
      MediaType("application", "mbms-msk-response+xml", compressible = true, binary = true)

    lazy val `mbms-protection-description+xml`: MediaType =
      MediaType("application", "mbms-protection-description+xml", compressible = true, binary = true)

    lazy val `mbms-reception-report+xml`: MediaType =
      MediaType("application", "mbms-reception-report+xml", compressible = true, binary = true)

    lazy val `mbms-register+xml`: MediaType =
      MediaType("application", "mbms-register+xml", compressible = true, binary = true)

    lazy val `mbms-register-response+xml`: MediaType =
      MediaType("application", "mbms-register-response+xml", compressible = true, binary = true)

    lazy val `mbms-schedule+xml`: MediaType =
      MediaType("application", "mbms-schedule+xml", compressible = true, binary = true)

    lazy val `mbms-user-service-description+xml`: MediaType =
      MediaType("application", "mbms-user-service-description+xml", compressible = true, binary = true)

    lazy val `mbox`: MediaType =
      MediaType("application", "mbox", compressible = false, binary = true, fileExtensions = List("mbox"))

    lazy val `media-policy-dataset+xml`: MediaType =
      MediaType(
        "application",
        "media-policy-dataset+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("mpf")
      )

    lazy val `media_control+xml`: MediaType =
      MediaType("application", "media_control+xml", compressible = true, binary = true)

    lazy val `mediaservercontrol+xml`: MediaType =
      MediaType(
        "application",
        "mediaservercontrol+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("mscml")
      )

    lazy val `merge-patch+json`: MediaType =
      MediaType("application", "merge-patch+json", compressible = true, binary = false)

    lazy val `metalink+xml`: MediaType =
      MediaType("application", "metalink+xml", compressible = true, binary = true, fileExtensions = List("metalink"))

    lazy val `metalink4+xml`: MediaType =
      MediaType("application", "metalink4+xml", compressible = true, binary = true, fileExtensions = List("meta4"))

    lazy val `mets+xml`: MediaType =
      MediaType("application", "mets+xml", compressible = true, binary = true, fileExtensions = List("mets"))

    lazy val `mf4`: MediaType =
      MediaType("application", "mf4", compressible = false, binary = true)

    lazy val `mikey`: MediaType =
      MediaType("application", "mikey", compressible = false, binary = true)

    lazy val `mipc`: MediaType =
      MediaType("application", "mipc", compressible = false, binary = true)

    lazy val `missing-blocks+cbor-seq`: MediaType =
      MediaType("application", "missing-blocks+cbor-seq", compressible = false, binary = true)

    lazy val `mmt-aei+xml`: MediaType =
      MediaType("application", "mmt-aei+xml", compressible = true, binary = true, fileExtensions = List("maei"))

    lazy val `mmt-usd+xml`: MediaType =
      MediaType("application", "mmt-usd+xml", compressible = true, binary = true, fileExtensions = List("musd"))

    lazy val `mods+xml`: MediaType =
      MediaType("application", "mods+xml", compressible = true, binary = true, fileExtensions = List("mods"))

    lazy val `moss-keys`: MediaType =
      MediaType("application", "moss-keys", compressible = false, binary = true)

    lazy val `moss-signature`: MediaType =
      MediaType("application", "moss-signature", compressible = false, binary = true)

    lazy val `mosskey-data`: MediaType =
      MediaType("application", "mosskey-data", compressible = false, binary = true)

    lazy val `mosskey-request`: MediaType =
      MediaType("application", "mosskey-request", compressible = false, binary = true)

    lazy val `mp21`: MediaType =
      MediaType("application", "mp21", compressible = false, binary = true, fileExtensions = List("m21", "mp21"))

    lazy val `mp4`: MediaType =
      MediaType(
        "application",
        "mp4",
        compressible = false,
        binary = true,
        fileExtensions = List("mp4", "mpg4", "mp4s", "m4p")
      )

    lazy val `mpeg4-generic`: MediaType =
      MediaType("application", "mpeg4-generic", compressible = false, binary = true)

    lazy val `mpeg4-iod`: MediaType =
      MediaType("application", "mpeg4-iod", compressible = false, binary = true)

    lazy val `mpeg4-iod-xmt`: MediaType =
      MediaType("application", "mpeg4-iod-xmt", compressible = false, binary = true)

    lazy val `mrb-consumer+xml`: MediaType =
      MediaType("application", "mrb-consumer+xml", compressible = true, binary = true)

    lazy val `mrb-publish+xml`: MediaType =
      MediaType("application", "mrb-publish+xml", compressible = true, binary = true)

    lazy val `msc-ivr+xml`: MediaType =
      MediaType("application", "msc-ivr+xml", compressible = true, binary = true)

    lazy val `msc-mixer+xml`: MediaType =
      MediaType("application", "msc-mixer+xml", compressible = true, binary = true)

    lazy val `msix`: MediaType =
      MediaType("application", "msix", compressible = false, binary = true, fileExtensions = List("msix"))

    lazy val `msixbundle`: MediaType =
      MediaType("application", "msixbundle", compressible = false, binary = true, fileExtensions = List("msixbundle"))

    lazy val `msword`: MediaType =
      MediaType("application", "msword", compressible = false, binary = true, fileExtensions = List("doc", "dot"))

    lazy val `mud+json`: MediaType =
      MediaType("application", "mud+json", compressible = true, binary = false)

    lazy val `multipart-core`: MediaType =
      MediaType("application", "multipart-core", compressible = false, binary = true)

    lazy val `mxf`: MediaType =
      MediaType("application", "mxf", compressible = false, binary = true, fileExtensions = List("mxf"))

    lazy val `n-quads`: MediaType =
      MediaType("application", "n-quads", compressible = false, binary = true, fileExtensions = List("nq"))

    lazy val `n-triples`: MediaType =
      MediaType("application", "n-triples", compressible = false, binary = true, fileExtensions = List("nt"))

    lazy val `nasdata`: MediaType =
      MediaType("application", "nasdata", compressible = false, binary = true)

    lazy val `news-checkgroups`: MediaType =
      MediaType("application", "news-checkgroups", compressible = false, binary = true)

    lazy val `news-groupinfo`: MediaType =
      MediaType("application", "news-groupinfo", compressible = false, binary = true)

    lazy val `news-transmission`: MediaType =
      MediaType("application", "news-transmission", compressible = false, binary = true)

    lazy val `nlsml+xml`: MediaType =
      MediaType("application", "nlsml+xml", compressible = true, binary = true)

    lazy val `node`: MediaType =
      MediaType("application", "node", compressible = false, binary = true, fileExtensions = List("cjs"))

    lazy val `nss`: MediaType =
      MediaType("application", "nss", compressible = false, binary = true)

    lazy val `oauth-authz-req+jwt`: MediaType =
      MediaType("application", "oauth-authz-req+jwt", compressible = false, binary = true)

    lazy val `oblivious-dns-message`: MediaType =
      MediaType("application", "oblivious-dns-message", compressible = false, binary = true)

    lazy val `ocsp-request`: MediaType =
      MediaType("application", "ocsp-request", compressible = false, binary = true)

    lazy val `ocsp-response`: MediaType =
      MediaType("application", "ocsp-response", compressible = false, binary = true)

    lazy val `octet-stream`: MediaType =
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

    lazy val `oda`: MediaType =
      MediaType("application", "oda", compressible = false, binary = true, fileExtensions = List("oda"))

    lazy val `odm+xml`: MediaType =
      MediaType("application", "odm+xml", compressible = true, binary = true)

    lazy val `odx`: MediaType =
      MediaType("application", "odx", compressible = false, binary = true)

    lazy val `oebps-package+xml`: MediaType =
      MediaType("application", "oebps-package+xml", compressible = true, binary = true, fileExtensions = List("opf"))

    lazy val `ogg`: MediaType =
      MediaType("application", "ogg", compressible = false, binary = true, fileExtensions = List("ogx"))

    lazy val `ohttp-keys`: MediaType =
      MediaType("application", "ohttp-keys", compressible = false, binary = true)

    lazy val `omdoc+xml`: MediaType =
      MediaType("application", "omdoc+xml", compressible = true, binary = true, fileExtensions = List("omdoc"))

    lazy val `onenote`: MediaType =
      MediaType(
        "application",
        "onenote",
        compressible = false,
        binary = true,
        fileExtensions = List("onetoc", "onetoc2", "onetmp", "onepkg", "one", "onea")
      )

    lazy val `opc-nodeset+xml`: MediaType =
      MediaType("application", "opc-nodeset+xml", compressible = true, binary = true)

    lazy val `oscore`: MediaType =
      MediaType("application", "oscore", compressible = false, binary = true)

    lazy val `oxps`: MediaType =
      MediaType("application", "oxps", compressible = false, binary = true, fileExtensions = List("oxps"))

    lazy val `p21`: MediaType =
      MediaType("application", "p21", compressible = false, binary = true)

    lazy val `p21+zip`: MediaType =
      MediaType("application", "p21+zip", compressible = false, binary = true)

    lazy val `p2p-overlay+xml`: MediaType =
      MediaType("application", "p2p-overlay+xml", compressible = true, binary = true, fileExtensions = List("relo"))

    lazy val `parityfec`: MediaType =
      MediaType("application", "parityfec", compressible = false, binary = true)

    lazy val `passport`: MediaType =
      MediaType("application", "passport", compressible = false, binary = true)

    lazy val `patch-ops-error+xml`: MediaType =
      MediaType("application", "patch-ops-error+xml", compressible = true, binary = true, fileExtensions = List("xer"))

    lazy val `pdf`: MediaType =
      MediaType("application", "pdf", compressible = false, binary = true, fileExtensions = List("pdf"))

    lazy val `pdx`: MediaType =
      MediaType("application", "pdx", compressible = false, binary = true)

    lazy val `pem-certificate-chain`: MediaType =
      MediaType("application", "pem-certificate-chain", compressible = false, binary = true)

    lazy val `pgp-encrypted`: MediaType =
      MediaType("application", "pgp-encrypted", compressible = false, binary = true, fileExtensions = List("pgp"))

    lazy val `pgp-keys`: MediaType =
      MediaType("application", "pgp-keys", compressible = false, binary = true, fileExtensions = List("asc"))

    lazy val `pgp-signature`: MediaType =
      MediaType(
        "application",
        "pgp-signature",
        compressible = false,
        binary = true,
        fileExtensions = List("sig", "asc")
      )

    lazy val `pics-rules`: MediaType =
      MediaType("application", "pics-rules", compressible = false, binary = true, fileExtensions = List("prf"))

    lazy val `pidf+xml`: MediaType =
      MediaType("application", "pidf+xml", compressible = true, binary = true)

    lazy val `pidf-diff+xml`: MediaType =
      MediaType("application", "pidf-diff+xml", compressible = true, binary = true)

    lazy val `pkcs10`: MediaType =
      MediaType("application", "pkcs10", compressible = false, binary = true, fileExtensions = List("p10"))

    lazy val `pkcs12`: MediaType =
      MediaType("application", "pkcs12", compressible = false, binary = true)

    lazy val `pkcs7-mime`: MediaType =
      MediaType("application", "pkcs7-mime", compressible = false, binary = true, fileExtensions = List("p7m", "p7c"))

    lazy val `pkcs7-signature`: MediaType =
      MediaType("application", "pkcs7-signature", compressible = false, binary = true, fileExtensions = List("p7s"))

    lazy val `pkcs8`: MediaType =
      MediaType("application", "pkcs8", compressible = false, binary = true, fileExtensions = List("p8"))

    lazy val `pkcs8-encrypted`: MediaType =
      MediaType("application", "pkcs8-encrypted", compressible = false, binary = true)

    lazy val `pkix-attr-cert`: MediaType =
      MediaType("application", "pkix-attr-cert", compressible = false, binary = true, fileExtensions = List("ac"))

    lazy val `pkix-cert`: MediaType =
      MediaType("application", "pkix-cert", compressible = false, binary = true, fileExtensions = List("cer"))

    lazy val `pkix-crl`: MediaType =
      MediaType("application", "pkix-crl", compressible = false, binary = true, fileExtensions = List("crl"))

    lazy val `pkix-pkipath`: MediaType =
      MediaType("application", "pkix-pkipath", compressible = false, binary = true, fileExtensions = List("pkipath"))

    lazy val `pkixcmp`: MediaType =
      MediaType("application", "pkixcmp", compressible = false, binary = true, fileExtensions = List("pki"))

    lazy val `pls+xml`: MediaType =
      MediaType("application", "pls+xml", compressible = true, binary = true, fileExtensions = List("pls"))

    lazy val `poc-settings+xml`: MediaType =
      MediaType("application", "poc-settings+xml", compressible = true, binary = true)

    lazy val `postscript`: MediaType =
      MediaType(
        "application",
        "postscript",
        compressible = true,
        binary = true,
        fileExtensions = List("ai", "eps", "ps")
      )

    lazy val `ppsp-tracker+json`: MediaType =
      MediaType("application", "ppsp-tracker+json", compressible = true, binary = false)

    lazy val `private-token-issuer-directory`: MediaType =
      MediaType("application", "private-token-issuer-directory", compressible = false, binary = true)

    lazy val `private-token-request`: MediaType =
      MediaType("application", "private-token-request", compressible = false, binary = true)

    lazy val `private-token-response`: MediaType =
      MediaType("application", "private-token-response", compressible = false, binary = true)

    lazy val `problem+json`: MediaType =
      MediaType("application", "problem+json", compressible = true, binary = false)

    lazy val `problem+xml`: MediaType =
      MediaType("application", "problem+xml", compressible = true, binary = true)

    lazy val `protobuf`: MediaType =
      MediaType("application", "protobuf", compressible = false, binary = true)

    lazy val `protobuf+json`: MediaType =
      MediaType("application", "protobuf+json", compressible = true, binary = false)

    lazy val `provenance+xml`: MediaType =
      MediaType("application", "provenance+xml", compressible = true, binary = true, fileExtensions = List("provx"))

    lazy val `provided-claims+jwt`: MediaType =
      MediaType("application", "provided-claims+jwt", compressible = false, binary = true)

    lazy val `prs.alvestrand.titrax-sheet`: MediaType =
      MediaType("application", "prs.alvestrand.titrax-sheet", compressible = false, binary = true)

    lazy val `prs.cww`: MediaType =
      MediaType("application", "prs.cww", compressible = false, binary = true, fileExtensions = List("cww"))

    lazy val `prs.cyn`: MediaType =
      MediaType("application", "prs.cyn", compressible = false, binary = true)

    lazy val `prs.hpub+zip`: MediaType =
      MediaType("application", "prs.hpub+zip", compressible = false, binary = true)

    lazy val `prs.implied-document+xml`: MediaType =
      MediaType("application", "prs.implied-document+xml", compressible = true, binary = true)

    lazy val `prs.implied-executable`: MediaType =
      MediaType("application", "prs.implied-executable", compressible = false, binary = true)

    lazy val `prs.implied-object+json`: MediaType =
      MediaType("application", "prs.implied-object+json", compressible = true, binary = false)

    lazy val `prs.implied-object+json-seq`: MediaType =
      MediaType("application", "prs.implied-object+json-seq", compressible = false, binary = true)

    lazy val `prs.implied-object+yaml`: MediaType =
      MediaType("application", "prs.implied-object+yaml", compressible = false, binary = true)

    lazy val `prs.implied-structure`: MediaType =
      MediaType("application", "prs.implied-structure", compressible = false, binary = true)

    lazy val `prs.mayfile`: MediaType =
      MediaType("application", "prs.mayfile", compressible = false, binary = true)

    lazy val `prs.nprend`: MediaType =
      MediaType("application", "prs.nprend", compressible = false, binary = true)

    lazy val `prs.plucker`: MediaType =
      MediaType("application", "prs.plucker", compressible = false, binary = true)

    lazy val `prs.rdf-xml-crypt`: MediaType =
      MediaType("application", "prs.rdf-xml-crypt", compressible = false, binary = true)

    lazy val `prs.sclt`: MediaType =
      MediaType("application", "prs.sclt", compressible = false, binary = true)

    lazy val `prs.vcfbzip2`: MediaType =
      MediaType("application", "prs.vcfbzip2", compressible = false, binary = true)

    lazy val `prs.xsf+xml`: MediaType =
      MediaType("application", "prs.xsf+xml", compressible = true, binary = true, fileExtensions = List("xsf"))

    lazy val `pskc+xml`: MediaType =
      MediaType("application", "pskc+xml", compressible = true, binary = true, fileExtensions = List("pskcxml"))

    lazy val `pvd+json`: MediaType =
      MediaType("application", "pvd+json", compressible = true, binary = false)

    lazy val `qsig`: MediaType =
      MediaType("application", "qsig", compressible = false, binary = true)

    lazy val `raml+yaml`: MediaType =
      MediaType("application", "raml+yaml", compressible = true, binary = true, fileExtensions = List("raml"))

    lazy val `raptorfec`: MediaType =
      MediaType("application", "raptorfec", compressible = false, binary = true)

    lazy val `rdap+json`: MediaType =
      MediaType("application", "rdap+json", compressible = true, binary = false)

    lazy val `rdf+xml`: MediaType =
      MediaType("application", "rdf+xml", compressible = true, binary = true, fileExtensions = List("rdf", "owl"))

    lazy val `reginfo+xml`: MediaType =
      MediaType("application", "reginfo+xml", compressible = true, binary = true, fileExtensions = List("rif"))

    lazy val `relax-ng-compact-syntax`: MediaType =
      MediaType(
        "application",
        "relax-ng-compact-syntax",
        compressible = false,
        binary = true,
        fileExtensions = List("rnc")
      )

    lazy val `remote-printing`: MediaType =
      MediaType("application", "remote-printing", compressible = false, binary = true)

    lazy val `reputon+json`: MediaType =
      MediaType("application", "reputon+json", compressible = true, binary = false)

    lazy val `resolve-response+jwt`: MediaType =
      MediaType("application", "resolve-response+jwt", compressible = false, binary = true)

    lazy val `resource-lists+xml`: MediaType =
      MediaType("application", "resource-lists+xml", compressible = true, binary = true, fileExtensions = List("rl"))

    lazy val `resource-lists-diff+xml`: MediaType =
      MediaType(
        "application",
        "resource-lists-diff+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("rld")
      )

    lazy val `rfc+xml`: MediaType =
      MediaType("application", "rfc+xml", compressible = true, binary = true)

    lazy val `riscos`: MediaType =
      MediaType("application", "riscos", compressible = false, binary = true)

    lazy val `rlmi+xml`: MediaType =
      MediaType("application", "rlmi+xml", compressible = true, binary = true)

    lazy val `rls-services+xml`: MediaType =
      MediaType("application", "rls-services+xml", compressible = true, binary = true, fileExtensions = List("rs"))

    lazy val `route-apd+xml`: MediaType =
      MediaType("application", "route-apd+xml", compressible = true, binary = true, fileExtensions = List("rapd"))

    lazy val `route-s-tsid+xml`: MediaType =
      MediaType("application", "route-s-tsid+xml", compressible = true, binary = true, fileExtensions = List("sls"))

    lazy val `route-usd+xml`: MediaType =
      MediaType("application", "route-usd+xml", compressible = true, binary = true, fileExtensions = List("rusd"))

    lazy val `rpki-checklist`: MediaType =
      MediaType("application", "rpki-checklist", compressible = false, binary = true)

    lazy val `rpki-ghostbusters`: MediaType =
      MediaType("application", "rpki-ghostbusters", compressible = false, binary = true, fileExtensions = List("gbr"))

    lazy val `rpki-manifest`: MediaType =
      MediaType("application", "rpki-manifest", compressible = false, binary = true, fileExtensions = List("mft"))

    lazy val `rpki-publication`: MediaType =
      MediaType("application", "rpki-publication", compressible = false, binary = true)

    lazy val `rpki-roa`: MediaType =
      MediaType("application", "rpki-roa", compressible = false, binary = true, fileExtensions = List("roa"))

    lazy val `rpki-signed-tal`: MediaType =
      MediaType("application", "rpki-signed-tal", compressible = false, binary = true)

    lazy val `rpki-updown`: MediaType =
      MediaType("application", "rpki-updown", compressible = false, binary = true)

    lazy val `rs-metadata+xml`: MediaType =
      MediaType("application", "rs-metadata+xml", compressible = true, binary = true)

    lazy val `rsd+xml`: MediaType =
      MediaType("application", "rsd+xml", compressible = true, binary = true, fileExtensions = List("rsd"))

    lazy val `rss+xml`: MediaType =
      MediaType("application", "rss+xml", compressible = true, binary = true, fileExtensions = List("rss"))

    lazy val `rtf`: MediaType =
      MediaType("application", "rtf", compressible = true, binary = true, fileExtensions = List("rtf"))

    lazy val `rtploopback`: MediaType =
      MediaType("application", "rtploopback", compressible = false, binary = true)

    lazy val `rtx`: MediaType =
      MediaType("application", "rtx", compressible = false, binary = true)

    lazy val `samlassertion+xml`: MediaType =
      MediaType("application", "samlassertion+xml", compressible = true, binary = true)

    lazy val `samlmetadata+xml`: MediaType =
      MediaType("application", "samlmetadata+xml", compressible = true, binary = true)

    lazy val `sarif+json`: MediaType =
      MediaType("application", "sarif+json", compressible = true, binary = false)

    lazy val `sarif-external-properties+json`: MediaType =
      MediaType("application", "sarif-external-properties+json", compressible = true, binary = false)

    lazy val `sbe`: MediaType =
      MediaType("application", "sbe", compressible = false, binary = true)

    lazy val `sbml+xml`: MediaType =
      MediaType("application", "sbml+xml", compressible = true, binary = true, fileExtensions = List("sbml"))

    lazy val `scaip+xml`: MediaType =
      MediaType("application", "scaip+xml", compressible = true, binary = true)

    lazy val `scim+json`: MediaType =
      MediaType("application", "scim+json", compressible = true, binary = false)

    lazy val `scitt-receipt+cose`: MediaType =
      MediaType("application", "scitt-receipt+cose", compressible = false, binary = true)

    lazy val `scitt-statement+cose`: MediaType =
      MediaType("application", "scitt-statement+cose", compressible = false, binary = true)

    lazy val `scvp-cv-request`: MediaType =
      MediaType("application", "scvp-cv-request", compressible = false, binary = true, fileExtensions = List("scq"))

    lazy val `scvp-cv-response`: MediaType =
      MediaType("application", "scvp-cv-response", compressible = false, binary = true, fileExtensions = List("scs"))

    lazy val `scvp-vp-request`: MediaType =
      MediaType("application", "scvp-vp-request", compressible = false, binary = true, fileExtensions = List("spq"))

    lazy val `scvp-vp-response`: MediaType =
      MediaType("application", "scvp-vp-response", compressible = false, binary = true, fileExtensions = List("spp"))

    lazy val `sd-jwt`: MediaType =
      MediaType("application", "sd-jwt", compressible = false, binary = true)

    lazy val `sd-jwt+json`: MediaType =
      MediaType("application", "sd-jwt+json", compressible = true, binary = false)

    lazy val `sdf+json`: MediaType =
      MediaType("application", "sdf+json", compressible = true, binary = false)

    lazy val `sdp`: MediaType =
      MediaType("application", "sdp", compressible = false, binary = true, fileExtensions = List("sdp"))

    lazy val `secevent+jwt`: MediaType =
      MediaType("application", "secevent+jwt", compressible = false, binary = true)

    lazy val `senml+cbor`: MediaType =
      MediaType("application", "senml+cbor", compressible = false, binary = true)

    lazy val `senml+json`: MediaType =
      MediaType("application", "senml+json", compressible = true, binary = false)

    lazy val `senml+xml`: MediaType =
      MediaType("application", "senml+xml", compressible = true, binary = true, fileExtensions = List("senmlx"))

    lazy val `senml-etch+cbor`: MediaType =
      MediaType("application", "senml-etch+cbor", compressible = false, binary = true)

    lazy val `senml-etch+json`: MediaType =
      MediaType("application", "senml-etch+json", compressible = true, binary = false)

    lazy val `senml-exi`: MediaType =
      MediaType("application", "senml-exi", compressible = false, binary = true)

    lazy val `sensml+cbor`: MediaType =
      MediaType("application", "sensml+cbor", compressible = false, binary = true)

    lazy val `sensml+json`: MediaType =
      MediaType("application", "sensml+json", compressible = true, binary = false)

    lazy val `sensml+xml`: MediaType =
      MediaType("application", "sensml+xml", compressible = true, binary = true, fileExtensions = List("sensmlx"))

    lazy val `sensml-exi`: MediaType =
      MediaType("application", "sensml-exi", compressible = false, binary = true)

    lazy val `sep+xml`: MediaType =
      MediaType("application", "sep+xml", compressible = true, binary = true)

    lazy val `sep-exi`: MediaType =
      MediaType("application", "sep-exi", compressible = false, binary = true)

    lazy val `session-info`: MediaType =
      MediaType("application", "session-info", compressible = false, binary = true)

    lazy val `set-payment`: MediaType =
      MediaType("application", "set-payment", compressible = false, binary = true)

    lazy val `set-payment-initiation`: MediaType =
      MediaType(
        "application",
        "set-payment-initiation",
        compressible = false,
        binary = true,
        fileExtensions = List("setpay")
      )

    lazy val `set-registration`: MediaType =
      MediaType("application", "set-registration", compressible = false, binary = true)

    lazy val `set-registration-initiation`: MediaType =
      MediaType(
        "application",
        "set-registration-initiation",
        compressible = false,
        binary = true,
        fileExtensions = List("setreg")
      )

    lazy val `sgml`: MediaType =
      MediaType("application", "sgml", compressible = false, binary = true)

    lazy val `sgml-open-catalog`: MediaType =
      MediaType("application", "sgml-open-catalog", compressible = false, binary = true)

    lazy val `shf+xml`: MediaType =
      MediaType("application", "shf+xml", compressible = true, binary = true, fileExtensions = List("shf"))

    lazy val `sieve`: MediaType =
      MediaType("application", "sieve", compressible = false, binary = true, fileExtensions = List("siv", "sieve"))

    lazy val `simple-filter+xml`: MediaType =
      MediaType("application", "simple-filter+xml", compressible = true, binary = true)

    lazy val `simple-message-summary`: MediaType =
      MediaType("application", "simple-message-summary", compressible = false, binary = true)

    lazy val `simplesymbolcontainer`: MediaType =
      MediaType("application", "simplesymbolcontainer", compressible = false, binary = true)

    lazy val `sipc`: MediaType =
      MediaType("application", "sipc", compressible = false, binary = true)

    lazy val `slate`: MediaType =
      MediaType("application", "slate", compressible = false, binary = true)

    lazy val `smil`: MediaType =
      MediaType("application", "smil", compressible = false, binary = true)

    lazy val `smil+xml`: MediaType =
      MediaType("application", "smil+xml", compressible = true, binary = true, fileExtensions = List("smi", "smil"))

    lazy val `smpte336m`: MediaType =
      MediaType("application", "smpte336m", compressible = false, binary = true)

    lazy val `soap+fastinfoset`: MediaType =
      MediaType("application", "soap+fastinfoset", compressible = false, binary = true)

    lazy val `soap+xml`: MediaType =
      MediaType("application", "soap+xml", compressible = true, binary = true)

    lazy val `sparql-query`: MediaType =
      MediaType("application", "sparql-query", compressible = false, binary = true, fileExtensions = List("rq"))

    lazy val `sparql-results+xml`: MediaType =
      MediaType("application", "sparql-results+xml", compressible = true, binary = true, fileExtensions = List("srx"))

    lazy val `spdx+json`: MediaType =
      MediaType("application", "spdx+json", compressible = true, binary = false)

    lazy val `spirits-event+xml`: MediaType =
      MediaType("application", "spirits-event+xml", compressible = true, binary = true)

    lazy val `sql`: MediaType =
      MediaType("application", "sql", compressible = false, binary = true, fileExtensions = List("sql"))

    lazy val `srgs`: MediaType =
      MediaType("application", "srgs", compressible = false, binary = true, fileExtensions = List("gram"))

    lazy val `srgs+xml`: MediaType =
      MediaType("application", "srgs+xml", compressible = true, binary = true, fileExtensions = List("grxml"))

    lazy val `sru+xml`: MediaType =
      MediaType("application", "sru+xml", compressible = true, binary = true, fileExtensions = List("sru"))

    lazy val `ssdl+xml`: MediaType =
      MediaType("application", "ssdl+xml", compressible = true, binary = true, fileExtensions = List("ssdl"))

    lazy val `sslkeylogfile`: MediaType =
      MediaType("application", "sslkeylogfile", compressible = false, binary = true)

    lazy val `ssml+xml`: MediaType =
      MediaType("application", "ssml+xml", compressible = true, binary = true, fileExtensions = List("ssml"))

    lazy val `st2110-41`: MediaType =
      MediaType("application", "st2110-41", compressible = false, binary = true)

    lazy val `stix+json`: MediaType =
      MediaType("application", "stix+json", compressible = true, binary = false)

    lazy val `stratum`: MediaType =
      MediaType("application", "stratum", compressible = false, binary = true)

    lazy val `suit-envelope+cose`: MediaType =
      MediaType("application", "suit-envelope+cose", compressible = false, binary = true)

    lazy val `suit-report+cose`: MediaType =
      MediaType("application", "suit-report+cose", compressible = false, binary = true)

    lazy val `swid+cbor`: MediaType =
      MediaType("application", "swid+cbor", compressible = false, binary = true)

    lazy val `swid+xml`: MediaType =
      MediaType("application", "swid+xml", compressible = true, binary = true, fileExtensions = List("swidtag"))

    lazy val `tamp-apex-update`: MediaType =
      MediaType("application", "tamp-apex-update", compressible = false, binary = true)

    lazy val `tamp-apex-update-confirm`: MediaType =
      MediaType("application", "tamp-apex-update-confirm", compressible = false, binary = true)

    lazy val `tamp-community-update`: MediaType =
      MediaType("application", "tamp-community-update", compressible = false, binary = true)

    lazy val `tamp-community-update-confirm`: MediaType =
      MediaType("application", "tamp-community-update-confirm", compressible = false, binary = true)

    lazy val `tamp-error`: MediaType =
      MediaType("application", "tamp-error", compressible = false, binary = true)

    lazy val `tamp-sequence-adjust`: MediaType =
      MediaType("application", "tamp-sequence-adjust", compressible = false, binary = true)

    lazy val `tamp-sequence-adjust-confirm`: MediaType =
      MediaType("application", "tamp-sequence-adjust-confirm", compressible = false, binary = true)

    lazy val `tamp-status-query`: MediaType =
      MediaType("application", "tamp-status-query", compressible = false, binary = true)

    lazy val `tamp-status-response`: MediaType =
      MediaType("application", "tamp-status-response", compressible = false, binary = true)

    lazy val `tamp-update`: MediaType =
      MediaType("application", "tamp-update", compressible = false, binary = true)

    lazy val `tamp-update-confirm`: MediaType =
      MediaType("application", "tamp-update-confirm", compressible = false, binary = true)

    lazy val `tar`: MediaType =
      MediaType("application", "tar", compressible = true, binary = true)

    lazy val `taxii+json`: MediaType =
      MediaType("application", "taxii+json", compressible = true, binary = false)

    lazy val `td+json`: MediaType =
      MediaType("application", "td+json", compressible = true, binary = false)

    lazy val `tei+xml`: MediaType =
      MediaType("application", "tei+xml", compressible = true, binary = true, fileExtensions = List("tei", "teicorpus"))

    lazy val `tetra_isi`: MediaType =
      MediaType("application", "tetra_isi", compressible = false, binary = true)

    lazy val `texinfo`: MediaType =
      MediaType("application", "texinfo", compressible = false, binary = true)

    lazy val `thraud+xml`: MediaType =
      MediaType("application", "thraud+xml", compressible = true, binary = true, fileExtensions = List("tfi"))

    lazy val `timestamp-query`: MediaType =
      MediaType("application", "timestamp-query", compressible = false, binary = true)

    lazy val `timestamp-reply`: MediaType =
      MediaType("application", "timestamp-reply", compressible = false, binary = true)

    lazy val `timestamped-data`: MediaType =
      MediaType("application", "timestamped-data", compressible = false, binary = true, fileExtensions = List("tsd"))

    lazy val `tlsrpt+gzip`: MediaType =
      MediaType("application", "tlsrpt+gzip", compressible = false, binary = true)

    lazy val `tlsrpt+json`: MediaType =
      MediaType("application", "tlsrpt+json", compressible = true, binary = false)

    lazy val `tm+json`: MediaType =
      MediaType("application", "tm+json", compressible = true, binary = false)

    lazy val `tnauthlist`: MediaType =
      MediaType("application", "tnauthlist", compressible = false, binary = true)

    lazy val `toc+cbor`: MediaType =
      MediaType("application", "toc+cbor", compressible = false, binary = true)

    lazy val `token-introspection+jwt`: MediaType =
      MediaType("application", "token-introspection+jwt", compressible = false, binary = true)

    lazy val `toml`: MediaType =
      MediaType("application", "toml", compressible = true, binary = true, fileExtensions = List("toml"))

    lazy val `trickle-ice-sdpfrag`: MediaType =
      MediaType("application", "trickle-ice-sdpfrag", compressible = false, binary = true)

    lazy val `trig`: MediaType =
      MediaType("application", "trig", compressible = false, binary = true, fileExtensions = List("trig"))

    lazy val `trust-chain+json`: MediaType =
      MediaType("application", "trust-chain+json", compressible = true, binary = false)

    lazy val `trust-mark+jwt`: MediaType =
      MediaType("application", "trust-mark+jwt", compressible = false, binary = true)

    lazy val `trust-mark-delegation+jwt`: MediaType =
      MediaType("application", "trust-mark-delegation+jwt", compressible = false, binary = true)

    lazy val `ttml+xml`: MediaType =
      MediaType("application", "ttml+xml", compressible = true, binary = true, fileExtensions = List("ttml"))

    lazy val `tve-trigger`: MediaType =
      MediaType("application", "tve-trigger", compressible = false, binary = true)

    lazy val `tzif`: MediaType =
      MediaType("application", "tzif", compressible = false, binary = true)

    lazy val `tzif-leap`: MediaType =
      MediaType("application", "tzif-leap", compressible = false, binary = true)

    lazy val `ubjson`: MediaType =
      MediaType("application", "ubjson", compressible = false, binary = false, fileExtensions = List("ubj"))

    lazy val `uccs+cbor`: MediaType =
      MediaType("application", "uccs+cbor", compressible = false, binary = true)

    lazy val `ujcs+json`: MediaType =
      MediaType("application", "ujcs+json", compressible = true, binary = false)

    lazy val `ulpfec`: MediaType =
      MediaType("application", "ulpfec", compressible = false, binary = true)

    lazy val `urc-grpsheet+xml`: MediaType =
      MediaType("application", "urc-grpsheet+xml", compressible = true, binary = true)

    lazy val `urc-ressheet+xml`: MediaType =
      MediaType("application", "urc-ressheet+xml", compressible = true, binary = true, fileExtensions = List("rsheet"))

    lazy val `urc-targetdesc+xml`: MediaType =
      MediaType("application", "urc-targetdesc+xml", compressible = true, binary = true, fileExtensions = List("td"))

    lazy val `urc-uisocketdesc+xml`: MediaType =
      MediaType("application", "urc-uisocketdesc+xml", compressible = true, binary = true)

    lazy val `vc`: MediaType =
      MediaType("application", "vc", compressible = false, binary = true)

    lazy val `vc+cose`: MediaType =
      MediaType("application", "vc+cose", compressible = false, binary = true)

    lazy val `vc+jwt`: MediaType =
      MediaType("application", "vc+jwt", compressible = false, binary = true)

    lazy val `vc+sd-jwt`: MediaType =
      MediaType("application", "vc+sd-jwt", compressible = false, binary = true)

    lazy val `vcard+json`: MediaType =
      MediaType("application", "vcard+json", compressible = true, binary = false)

    lazy val `vcard+xml`: MediaType =
      MediaType("application", "vcard+xml", compressible = true, binary = true)

    lazy val `vec+xml`: MediaType =
      MediaType("application", "vec+xml", compressible = true, binary = true, fileExtensions = List("vec"))

    lazy val `vec-package+gzip`: MediaType =
      MediaType("application", "vec-package+gzip", compressible = false, binary = true)

    lazy val `vec-package+zip`: MediaType =
      MediaType("application", "vec-package+zip", compressible = false, binary = true)

    lazy val `vemmi`: MediaType =
      MediaType("application", "vemmi", compressible = false, binary = true)

    lazy val `vividence.scriptfile`: MediaType =
      MediaType("application", "vividence.scriptfile", compressible = false, binary = true)

    lazy val `vnd.1000minds.decision-model+xml`: MediaType =
      MediaType(
        "application",
        "vnd.1000minds.decision-model+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("1km")
      )

    lazy val `vnd.1ob`: MediaType =
      MediaType("application", "vnd.1ob", compressible = false, binary = true)

    lazy val `vnd.3gpp-prose+xml`: MediaType =
      MediaType("application", "vnd.3gpp-prose+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp-prose-pc3a+xml`: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc3a+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp-prose-pc3ach+xml`: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc3ach+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp-prose-pc3ch+xml`: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc3ch+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp-prose-pc8+xml`: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc8+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp-v2x-local-service-information`: MediaType =
      MediaType("application", "vnd.3gpp-v2x-local-service-information", compressible = false, binary = true)

    lazy val `vnd.3gpp.5gnas`: MediaType =
      MediaType("application", "vnd.3gpp.5gnas", compressible = false, binary = true)

    lazy val `vnd.3gpp.5gsa2x`: MediaType =
      MediaType("application", "vnd.3gpp.5gsa2x", compressible = false, binary = true)

    lazy val `vnd.3gpp.5gsa2x-local-service-information`: MediaType =
      MediaType("application", "vnd.3gpp.5gsa2x-local-service-information", compressible = false, binary = true)

    lazy val `vnd.3gpp.5gsv2x`: MediaType =
      MediaType("application", "vnd.3gpp.5gsv2x", compressible = false, binary = true)

    lazy val `vnd.3gpp.5gsv2x-local-service-information`: MediaType =
      MediaType("application", "vnd.3gpp.5gsv2x-local-service-information", compressible = false, binary = true)

    lazy val `vnd.3gpp.access-transfer-events+xml`: MediaType =
      MediaType("application", "vnd.3gpp.access-transfer-events+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.bsf+xml`: MediaType =
      MediaType("application", "vnd.3gpp.bsf+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.crs+xml`: MediaType =
      MediaType("application", "vnd.3gpp.crs+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.current-location-discovery+xml`: MediaType =
      MediaType("application", "vnd.3gpp.current-location-discovery+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.gmop+xml`: MediaType =
      MediaType("application", "vnd.3gpp.gmop+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.gtpc`: MediaType =
      MediaType("application", "vnd.3gpp.gtpc", compressible = false, binary = true)

    lazy val `vnd.3gpp.interworking-data`: MediaType =
      MediaType("application", "vnd.3gpp.interworking-data", compressible = false, binary = true)

    lazy val `vnd.3gpp.lpp`: MediaType =
      MediaType("application", "vnd.3gpp.lpp", compressible = false, binary = true)

    lazy val `vnd.3gpp.mc-signalling-ear`: MediaType =
      MediaType("application", "vnd.3gpp.mc-signalling-ear", compressible = false, binary = true)

    lazy val `vnd.3gpp.mcdata-affiliation-command+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-affiliation-command+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcdata-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcdata-msgstore-ctrl-request+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-msgstore-ctrl-request+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcdata-payload`: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-payload", compressible = false, binary = true)

    lazy val `vnd.3gpp.mcdata-regroup+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-regroup+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcdata-service-config+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-service-config+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcdata-signalling`: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-signalling", compressible = false, binary = true)

    lazy val `vnd.3gpp.mcdata-ue-config+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-ue-config+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcdata-user-profile+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-user-profile+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-affiliation-command+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-affiliation-command+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-floor-request+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-floor-request+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-location-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-location-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-mbms-usage-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-mbms-usage-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-regroup+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-regroup+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-service-config+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-service-config+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-signed+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-signed+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-ue-config+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-ue-config+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-ue-init-config+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-ue-init-config+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcptt-user-profile+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-user-profile+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcvideo-affiliation-command+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-affiliation-command+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcvideo-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcvideo-location-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-location-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcvideo-mbms-usage-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-mbms-usage-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcvideo-regroup+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-regroup+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcvideo-service-config+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-service-config+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcvideo-transmission-request+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-transmission-request+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcvideo-ue-config+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-ue-config+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mcvideo-user-profile+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-user-profile+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.mid-call+xml`: MediaType =
      MediaType("application", "vnd.3gpp.mid-call+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.ngap`: MediaType =
      MediaType("application", "vnd.3gpp.ngap", compressible = false, binary = true)

    lazy val `vnd.3gpp.pfcp`: MediaType =
      MediaType("application", "vnd.3gpp.pfcp", compressible = false, binary = true)

    lazy val `vnd.3gpp.pic-bw-large`: MediaType =
      MediaType(
        "application",
        "vnd.3gpp.pic-bw-large",
        compressible = false,
        binary = true,
        fileExtensions = List("plb")
      )

    lazy val `vnd.3gpp.pic-bw-small`: MediaType =
      MediaType(
        "application",
        "vnd.3gpp.pic-bw-small",
        compressible = false,
        binary = true,
        fileExtensions = List("psb")
      )

    lazy val `vnd.3gpp.pic-bw-var`: MediaType =
      MediaType("application", "vnd.3gpp.pic-bw-var", compressible = false, binary = true, fileExtensions = List("pvb"))

    lazy val `vnd.3gpp.pinapp-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.pinapp-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.s1ap`: MediaType =
      MediaType("application", "vnd.3gpp.s1ap", compressible = false, binary = true)

    lazy val `vnd.3gpp.seal-app-comm-requirements-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-app-comm-requirements-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-data-delivery-info+cbor`: MediaType =
      MediaType("application", "vnd.3gpp.seal-data-delivery-info+cbor", compressible = false, binary = true)

    lazy val `vnd.3gpp.seal-data-delivery-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-data-delivery-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-group-doc+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-group-doc+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-location-info+cbor`: MediaType =
      MediaType("application", "vnd.3gpp.seal-location-info+cbor", compressible = false, binary = true)

    lazy val `vnd.3gpp.seal-location-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-location-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-mbms-usage-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-mbms-usage-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-mbs-usage-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-mbs-usage-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-network-qos-management-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-network-qos-management-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-network-resource-info+cbor`: MediaType =
      MediaType("application", "vnd.3gpp.seal-network-resource-info+cbor", compressible = false, binary = true)

    lazy val `vnd.3gpp.seal-ue-config-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-ue-config-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-unicast-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-unicast-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.seal-user-profile-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.seal-user-profile-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.sms`: MediaType =
      MediaType("application", "vnd.3gpp.sms", compressible = false, binary = true)

    lazy val `vnd.3gpp.sms+xml`: MediaType =
      MediaType("application", "vnd.3gpp.sms+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.srvcc-ext+xml`: MediaType =
      MediaType("application", "vnd.3gpp.srvcc-ext+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.srvcc-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.srvcc-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.state-and-event-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.state-and-event-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.ussd+xml`: MediaType =
      MediaType("application", "vnd.3gpp.ussd+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp.v2x`: MediaType =
      MediaType("application", "vnd.3gpp.v2x", compressible = false, binary = true)

    lazy val `vnd.3gpp.vae-info+xml`: MediaType =
      MediaType("application", "vnd.3gpp.vae-info+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp2.bcmcsinfo+xml`: MediaType =
      MediaType("application", "vnd.3gpp2.bcmcsinfo+xml", compressible = true, binary = true)

    lazy val `vnd.3gpp2.sms`: MediaType =
      MediaType("application", "vnd.3gpp2.sms", compressible = false, binary = true)

    lazy val `vnd.3gpp2.tcap`: MediaType =
      MediaType("application", "vnd.3gpp2.tcap", compressible = false, binary = true, fileExtensions = List("tcap"))

    lazy val `vnd.3lightssoftware.imagescal`: MediaType =
      MediaType("application", "vnd.3lightssoftware.imagescal", compressible = false, binary = true)

    lazy val `vnd.3m.post-it-notes`: MediaType =
      MediaType(
        "application",
        "vnd.3m.post-it-notes",
        compressible = false,
        binary = true,
        fileExtensions = List("pwn")
      )

    lazy val `vnd.accpac.simply.aso`: MediaType =
      MediaType(
        "application",
        "vnd.accpac.simply.aso",
        compressible = false,
        binary = true,
        fileExtensions = List("aso")
      )

    lazy val `vnd.accpac.simply.imp`: MediaType =
      MediaType(
        "application",
        "vnd.accpac.simply.imp",
        compressible = false,
        binary = true,
        fileExtensions = List("imp")
      )

    lazy val `vnd.acm.addressxfer+json`: MediaType =
      MediaType("application", "vnd.acm.addressxfer+json", compressible = true, binary = false)

    lazy val `vnd.acm.chatbot+json`: MediaType =
      MediaType("application", "vnd.acm.chatbot+json", compressible = true, binary = false)

    lazy val `vnd.acucobol`: MediaType =
      MediaType("application", "vnd.acucobol", compressible = false, binary = true, fileExtensions = List("acu"))

    lazy val `vnd.acucorp`: MediaType =
      MediaType(
        "application",
        "vnd.acucorp",
        compressible = false,
        binary = true,
        fileExtensions = List("atc", "acutc")
      )

    lazy val `vnd.adobe.air-application-installer-package+zip`: MediaType =
      MediaType(
        "application",
        "vnd.adobe.air-application-installer-package+zip",
        compressible = false,
        binary = true,
        fileExtensions = List("air")
      )

    lazy val `vnd.adobe.flash.movie`: MediaType =
      MediaType("application", "vnd.adobe.flash.movie", compressible = false, binary = true)

    lazy val `vnd.adobe.formscentral.fcdt`: MediaType =
      MediaType(
        "application",
        "vnd.adobe.formscentral.fcdt",
        compressible = false,
        binary = true,
        fileExtensions = List("fcdt")
      )

    lazy val `vnd.adobe.fxp`: MediaType =
      MediaType(
        "application",
        "vnd.adobe.fxp",
        compressible = false,
        binary = true,
        fileExtensions = List("fxp", "fxpl")
      )

    lazy val `vnd.adobe.partial-upload`: MediaType =
      MediaType("application", "vnd.adobe.partial-upload", compressible = false, binary = true)

    lazy val `vnd.adobe.xdp+xml`: MediaType =
      MediaType("application", "vnd.adobe.xdp+xml", compressible = true, binary = true, fileExtensions = List("xdp"))

    lazy val `vnd.adobe.xfdf`: MediaType =
      MediaType("application", "vnd.adobe.xfdf", compressible = false, binary = true, fileExtensions = List("xfdf"))

    lazy val `vnd.aether.imp`: MediaType =
      MediaType("application", "vnd.aether.imp", compressible = false, binary = true)

    lazy val `vnd.afpc.afplinedata`: MediaType =
      MediaType("application", "vnd.afpc.afplinedata", compressible = false, binary = true)

    lazy val `vnd.afpc.afplinedata-pagedef`: MediaType =
      MediaType("application", "vnd.afpc.afplinedata-pagedef", compressible = false, binary = true)

    lazy val `vnd.afpc.cmoca-cmresource`: MediaType =
      MediaType("application", "vnd.afpc.cmoca-cmresource", compressible = false, binary = true)

    lazy val `vnd.afpc.foca-charset`: MediaType =
      MediaType("application", "vnd.afpc.foca-charset", compressible = false, binary = true)

    lazy val `vnd.afpc.foca-codedfont`: MediaType =
      MediaType("application", "vnd.afpc.foca-codedfont", compressible = false, binary = true)

    lazy val `vnd.afpc.foca-codepage`: MediaType =
      MediaType("application", "vnd.afpc.foca-codepage", compressible = false, binary = true)

    lazy val `vnd.afpc.modca`: MediaType =
      MediaType("application", "vnd.afpc.modca", compressible = false, binary = true)

    lazy val `vnd.afpc.modca-cmtable`: MediaType =
      MediaType("application", "vnd.afpc.modca-cmtable", compressible = false, binary = true)

    lazy val `vnd.afpc.modca-formdef`: MediaType =
      MediaType("application", "vnd.afpc.modca-formdef", compressible = false, binary = true)

    lazy val `vnd.afpc.modca-mediummap`: MediaType =
      MediaType("application", "vnd.afpc.modca-mediummap", compressible = false, binary = true)

    lazy val `vnd.afpc.modca-objectcontainer`: MediaType =
      MediaType("application", "vnd.afpc.modca-objectcontainer", compressible = false, binary = true)

    lazy val `vnd.afpc.modca-overlay`: MediaType =
      MediaType("application", "vnd.afpc.modca-overlay", compressible = false, binary = true)

    lazy val `vnd.afpc.modca-pagesegment`: MediaType =
      MediaType("application", "vnd.afpc.modca-pagesegment", compressible = false, binary = true)

    lazy val `vnd.age`: MediaType =
      MediaType("application", "vnd.age", compressible = false, binary = true, fileExtensions = List("age"))

    lazy val `vnd.ah-barcode`: MediaType =
      MediaType("application", "vnd.ah-barcode", compressible = false, binary = true)

    lazy val `vnd.ahead.space`: MediaType =
      MediaType("application", "vnd.ahead.space", compressible = false, binary = true, fileExtensions = List("ahead"))

    lazy val `vnd.aia`: MediaType =
      MediaType("application", "vnd.aia", compressible = false, binary = true)

    lazy val `vnd.airzip.filesecure.azf`: MediaType =
      MediaType(
        "application",
        "vnd.airzip.filesecure.azf",
        compressible = false,
        binary = true,
        fileExtensions = List("azf")
      )

    lazy val `vnd.airzip.filesecure.azs`: MediaType =
      MediaType(
        "application",
        "vnd.airzip.filesecure.azs",
        compressible = false,
        binary = true,
        fileExtensions = List("azs")
      )

    lazy val `vnd.amadeus+json`: MediaType =
      MediaType("application", "vnd.amadeus+json", compressible = true, binary = false)

    lazy val `vnd.amazon.ebook`: MediaType =
      MediaType("application", "vnd.amazon.ebook", compressible = false, binary = true, fileExtensions = List("azw"))

    lazy val `vnd.amazon.mobi8-ebook`: MediaType =
      MediaType("application", "vnd.amazon.mobi8-ebook", compressible = false, binary = true)

    lazy val `vnd.americandynamics.acc`: MediaType =
      MediaType(
        "application",
        "vnd.americandynamics.acc",
        compressible = false,
        binary = true,
        fileExtensions = List("acc")
      )

    lazy val `vnd.amiga.ami`: MediaType =
      MediaType("application", "vnd.amiga.ami", compressible = false, binary = true, fileExtensions = List("ami"))

    lazy val `vnd.amundsen.maze+xml`: MediaType =
      MediaType("application", "vnd.amundsen.maze+xml", compressible = true, binary = true)

    lazy val `vnd.android.ota`: MediaType =
      MediaType("application", "vnd.android.ota", compressible = false, binary = true)

    lazy val `vnd.android.package-archive`: MediaType =
      MediaType(
        "application",
        "vnd.android.package-archive",
        compressible = false,
        binary = true,
        fileExtensions = List("apk")
      )

    lazy val `vnd.anki`: MediaType =
      MediaType("application", "vnd.anki", compressible = false, binary = true)

    lazy val `vnd.anser-web-certificate-issue-initiation`: MediaType =
      MediaType(
        "application",
        "vnd.anser-web-certificate-issue-initiation",
        compressible = false,
        binary = true,
        fileExtensions = List("cii")
      )

    lazy val `vnd.anser-web-funds-transfer-initiation`: MediaType =
      MediaType(
        "application",
        "vnd.anser-web-funds-transfer-initiation",
        compressible = false,
        binary = true,
        fileExtensions = List("fti")
      )

    lazy val `vnd.antix.game-component`: MediaType =
      MediaType(
        "application",
        "vnd.antix.game-component",
        compressible = false,
        binary = true,
        fileExtensions = List("atx")
      )

    lazy val `vnd.apache.arrow.file`: MediaType =
      MediaType("application", "vnd.apache.arrow.file", compressible = false, binary = true)

    lazy val `vnd.apache.arrow.stream`: MediaType =
      MediaType("application", "vnd.apache.arrow.stream", compressible = false, binary = true)

    lazy val `vnd.apache.parquet`: MediaType =
      MediaType(
        "application",
        "vnd.apache.parquet",
        compressible = false,
        binary = true,
        fileExtensions = List("parquet")
      )

    lazy val `vnd.apache.thrift.binary`: MediaType =
      MediaType("application", "vnd.apache.thrift.binary", compressible = false, binary = true)

    lazy val `vnd.apache.thrift.compact`: MediaType =
      MediaType("application", "vnd.apache.thrift.compact", compressible = false, binary = true)

    lazy val `vnd.apache.thrift.json`: MediaType =
      MediaType("application", "vnd.apache.thrift.json", compressible = false, binary = false)

    lazy val `vnd.apexlang`: MediaType =
      MediaType("application", "vnd.apexlang", compressible = false, binary = true)

    lazy val `vnd.api+json`: MediaType =
      MediaType("application", "vnd.api+json", compressible = true, binary = false)

    lazy val `vnd.aplextor.warrp+json`: MediaType =
      MediaType("application", "vnd.aplextor.warrp+json", compressible = true, binary = false)

    lazy val `vnd.apothekende.reservation+json`: MediaType =
      MediaType("application", "vnd.apothekende.reservation+json", compressible = true, binary = false)

    lazy val `vnd.apple.installer+xml`: MediaType =
      MediaType(
        "application",
        "vnd.apple.installer+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("mpkg")
      )

    lazy val `vnd.apple.keynote`: MediaType =
      MediaType("application", "vnd.apple.keynote", compressible = false, binary = true, fileExtensions = List("key"))

    lazy val `vnd.apple.mpegurl`: MediaType =
      MediaType("application", "vnd.apple.mpegurl", compressible = false, binary = true, fileExtensions = List("m3u8"))

    lazy val `vnd.apple.numbers`: MediaType =
      MediaType(
        "application",
        "vnd.apple.numbers",
        compressible = false,
        binary = true,
        fileExtensions = List("numbers")
      )

    lazy val `vnd.apple.pages`: MediaType =
      MediaType("application", "vnd.apple.pages", compressible = false, binary = true, fileExtensions = List("pages"))

    lazy val `vnd.apple.pkpass`: MediaType =
      MediaType("application", "vnd.apple.pkpass", compressible = false, binary = true, fileExtensions = List("pkpass"))

    lazy val `vnd.arastra.swi`: MediaType =
      MediaType("application", "vnd.arastra.swi", compressible = false, binary = true)

    lazy val `vnd.aristanetworks.swi`: MediaType =
      MediaType(
        "application",
        "vnd.aristanetworks.swi",
        compressible = false,
        binary = true,
        fileExtensions = List("swi")
      )

    lazy val `vnd.artisan+json`: MediaType =
      MediaType("application", "vnd.artisan+json", compressible = true, binary = false)

    lazy val `vnd.artsquare`: MediaType =
      MediaType("application", "vnd.artsquare", compressible = false, binary = true)

    lazy val `vnd.as207960.vas.config+jer`: MediaType =
      MediaType("application", "vnd.as207960.vas.config+jer", compressible = false, binary = true)

    lazy val `vnd.as207960.vas.config+uper`: MediaType =
      MediaType("application", "vnd.as207960.vas.config+uper", compressible = false, binary = true)

    lazy val `vnd.as207960.vas.tap+jer`: MediaType =
      MediaType("application", "vnd.as207960.vas.tap+jer", compressible = false, binary = true)

    lazy val `vnd.as207960.vas.tap+uper`: MediaType =
      MediaType("application", "vnd.as207960.vas.tap+uper", compressible = false, binary = true)

    lazy val `vnd.astraea-software.iota`: MediaType =
      MediaType(
        "application",
        "vnd.astraea-software.iota",
        compressible = false,
        binary = true,
        fileExtensions = List("iota")
      )

    lazy val `vnd.audiograph`: MediaType =
      MediaType("application", "vnd.audiograph", compressible = false, binary = true, fileExtensions = List("aep"))

    lazy val `vnd.autodesk.fbx`: MediaType =
      MediaType("application", "vnd.autodesk.fbx", compressible = false, binary = true, fileExtensions = List("fbx"))

    lazy val `vnd.autopackage`: MediaType =
      MediaType("application", "vnd.autopackage", compressible = false, binary = true)

    lazy val `vnd.avalon+json`: MediaType =
      MediaType("application", "vnd.avalon+json", compressible = true, binary = false)

    lazy val `vnd.avistar+xml`: MediaType =
      MediaType("application", "vnd.avistar+xml", compressible = true, binary = true)

    lazy val `vnd.balsamiq.bmml+xml`: MediaType =
      MediaType(
        "application",
        "vnd.balsamiq.bmml+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("bmml")
      )

    lazy val `vnd.balsamiq.bmpr`: MediaType =
      MediaType("application", "vnd.balsamiq.bmpr", compressible = false, binary = true)

    lazy val `vnd.banana-accounting`: MediaType =
      MediaType("application", "vnd.banana-accounting", compressible = false, binary = true)

    lazy val `vnd.bbf.usp.error`: MediaType =
      MediaType("application", "vnd.bbf.usp.error", compressible = false, binary = true)

    lazy val `vnd.bbf.usp.msg`: MediaType =
      MediaType("application", "vnd.bbf.usp.msg", compressible = false, binary = true)

    lazy val `vnd.bbf.usp.msg+json`: MediaType =
      MediaType("application", "vnd.bbf.usp.msg+json", compressible = true, binary = false)

    lazy val `vnd.bekitzur-stech+json`: MediaType =
      MediaType("application", "vnd.bekitzur-stech+json", compressible = true, binary = false)

    lazy val `vnd.belightsoft.lhzd+zip`: MediaType =
      MediaType("application", "vnd.belightsoft.lhzd+zip", compressible = false, binary = true)

    lazy val `vnd.belightsoft.lhzl+zip`: MediaType =
      MediaType("application", "vnd.belightsoft.lhzl+zip", compressible = false, binary = true)

    lazy val `vnd.bint.med-content`: MediaType =
      MediaType("application", "vnd.bint.med-content", compressible = false, binary = true)

    lazy val `vnd.biopax.rdf+xml`: MediaType =
      MediaType("application", "vnd.biopax.rdf+xml", compressible = true, binary = true)

    lazy val `vnd.blink-idb-value-wrapper`: MediaType =
      MediaType("application", "vnd.blink-idb-value-wrapper", compressible = false, binary = true)

    lazy val `vnd.blueice.multipass`: MediaType =
      MediaType(
        "application",
        "vnd.blueice.multipass",
        compressible = false,
        binary = true,
        fileExtensions = List("mpm")
      )

    lazy val `vnd.bluetooth.ep.oob`: MediaType =
      MediaType("application", "vnd.bluetooth.ep.oob", compressible = false, binary = true)

    lazy val `vnd.bluetooth.le.oob`: MediaType =
      MediaType("application", "vnd.bluetooth.le.oob", compressible = false, binary = true)

    lazy val `vnd.bmi`: MediaType =
      MediaType("application", "vnd.bmi", compressible = false, binary = true, fileExtensions = List("bmi"))

    lazy val `vnd.bpf`: MediaType =
      MediaType("application", "vnd.bpf", compressible = false, binary = true)

    lazy val `vnd.bpf3`: MediaType =
      MediaType("application", "vnd.bpf3", compressible = false, binary = true)

    lazy val `vnd.businessobjects`: MediaType =
      MediaType("application", "vnd.businessobjects", compressible = false, binary = true, fileExtensions = List("rep"))

    lazy val `vnd.byu.uapi+json`: MediaType =
      MediaType("application", "vnd.byu.uapi+json", compressible = true, binary = false)

    lazy val `vnd.bzip3`: MediaType =
      MediaType("application", "vnd.bzip3", compressible = false, binary = true)

    lazy val `vnd.c3voc.schedule+xml`: MediaType =
      MediaType("application", "vnd.c3voc.schedule+xml", compressible = true, binary = true)

    lazy val `vnd.cab-jscript`: MediaType =
      MediaType("application", "vnd.cab-jscript", compressible = false, binary = true)

    lazy val `vnd.canon-cpdl`: MediaType =
      MediaType("application", "vnd.canon-cpdl", compressible = false, binary = true)

    lazy val `vnd.canon-lips`: MediaType =
      MediaType("application", "vnd.canon-lips", compressible = false, binary = true)

    lazy val `vnd.capasystems-pg+json`: MediaType =
      MediaType("application", "vnd.capasystems-pg+json", compressible = true, binary = false)

    lazy val `vnd.cel`: MediaType =
      MediaType("application", "vnd.cel", compressible = false, binary = true)

    lazy val `vnd.cendio.thinlinc.clientconf`: MediaType =
      MediaType("application", "vnd.cendio.thinlinc.clientconf", compressible = false, binary = true)

    lazy val `vnd.century-systems.tcp_stream`: MediaType =
      MediaType("application", "vnd.century-systems.tcp_stream", compressible = false, binary = true)

    lazy val `vnd.chemdraw+xml`: MediaType =
      MediaType("application", "vnd.chemdraw+xml", compressible = true, binary = true, fileExtensions = List("cdxml"))

    lazy val `vnd.chess-pgn`: MediaType =
      MediaType("application", "vnd.chess-pgn", compressible = false, binary = true)

    lazy val `vnd.chipnuts.karaoke-mmd`: MediaType =
      MediaType(
        "application",
        "vnd.chipnuts.karaoke-mmd",
        compressible = false,
        binary = true,
        fileExtensions = List("mmd")
      )

    lazy val `vnd.ciedi`: MediaType =
      MediaType("application", "vnd.ciedi", compressible = false, binary = true)

    lazy val `vnd.cinderella`: MediaType =
      MediaType("application", "vnd.cinderella", compressible = false, binary = true, fileExtensions = List("cdy"))

    lazy val `vnd.cirpack.isdn-ext`: MediaType =
      MediaType("application", "vnd.cirpack.isdn-ext", compressible = false, binary = true)

    lazy val `vnd.citationstyles.style+xml`: MediaType =
      MediaType(
        "application",
        "vnd.citationstyles.style+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("csl")
      )

    lazy val `vnd.claymore`: MediaType =
      MediaType("application", "vnd.claymore", compressible = false, binary = true, fileExtensions = List("cla"))

    lazy val `vnd.cloanto.rp9`: MediaType =
      MediaType("application", "vnd.cloanto.rp9", compressible = false, binary = true, fileExtensions = List("rp9"))

    lazy val `vnd.clonk.c4group`: MediaType =
      MediaType(
        "application",
        "vnd.clonk.c4group",
        compressible = false,
        binary = true,
        fileExtensions = List("c4g", "c4d", "c4f", "c4p", "c4u")
      )

    lazy val `vnd.cluetrust.cartomobile-config`: MediaType =
      MediaType(
        "application",
        "vnd.cluetrust.cartomobile-config",
        compressible = false,
        binary = true,
        fileExtensions = List("c11amc")
      )

    lazy val `vnd.cluetrust.cartomobile-config-pkg`: MediaType =
      MediaType(
        "application",
        "vnd.cluetrust.cartomobile-config-pkg",
        compressible = false,
        binary = true,
        fileExtensions = List("c11amz")
      )

    lazy val `vnd.cncf.helm.chart.content.v1.tar+gzip`: MediaType =
      MediaType("application", "vnd.cncf.helm.chart.content.v1.tar+gzip", compressible = false, binary = true)

    lazy val `vnd.cncf.helm.chart.provenance.v1.prov`: MediaType =
      MediaType("application", "vnd.cncf.helm.chart.provenance.v1.prov", compressible = false, binary = true)

    lazy val `vnd.cncf.helm.config.v1+json`: MediaType =
      MediaType("application", "vnd.cncf.helm.config.v1+json", compressible = true, binary = false)

    lazy val `vnd.coffeescript`: MediaType =
      MediaType("application", "vnd.coffeescript", compressible = false, binary = true)

    lazy val `vnd.collabio.xodocuments.document`: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.document", compressible = false, binary = true)

    lazy val `vnd.collabio.xodocuments.document-template`: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.document-template", compressible = false, binary = true)

    lazy val `vnd.collabio.xodocuments.presentation`: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.presentation", compressible = false, binary = true)

    lazy val `vnd.collabio.xodocuments.presentation-template`: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.presentation-template", compressible = false, binary = true)

    lazy val `vnd.collabio.xodocuments.spreadsheet`: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.spreadsheet", compressible = false, binary = true)

    lazy val `vnd.collabio.xodocuments.spreadsheet-template`: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.spreadsheet-template", compressible = false, binary = true)

    lazy val `vnd.collection+json`: MediaType =
      MediaType("application", "vnd.collection+json", compressible = true, binary = false)

    lazy val `vnd.collection.doc+json`: MediaType =
      MediaType("application", "vnd.collection.doc+json", compressible = true, binary = false)

    lazy val `vnd.collection.next+json`: MediaType =
      MediaType("application", "vnd.collection.next+json", compressible = true, binary = false)

    lazy val `vnd.comicbook+zip`: MediaType =
      MediaType("application", "vnd.comicbook+zip", compressible = false, binary = true)

    lazy val `vnd.comicbook-rar`: MediaType =
      MediaType("application", "vnd.comicbook-rar", compressible = false, binary = true)

    lazy val `vnd.commerce-battelle`: MediaType =
      MediaType("application", "vnd.commerce-battelle", compressible = false, binary = true)

    lazy val `vnd.commonspace`: MediaType =
      MediaType("application", "vnd.commonspace", compressible = false, binary = true, fileExtensions = List("csp"))

    lazy val `vnd.contact.cmsg`: MediaType =
      MediaType(
        "application",
        "vnd.contact.cmsg",
        compressible = false,
        binary = true,
        fileExtensions = List("cdbcmsg")
      )

    lazy val `vnd.coreos.ignition+json`: MediaType =
      MediaType("application", "vnd.coreos.ignition+json", compressible = true, binary = false)

    lazy val `vnd.cosmocaller`: MediaType =
      MediaType("application", "vnd.cosmocaller", compressible = false, binary = true, fileExtensions = List("cmc"))

    lazy val `vnd.crick.clicker`: MediaType =
      MediaType("application", "vnd.crick.clicker", compressible = false, binary = true, fileExtensions = List("clkx"))

    lazy val `vnd.crick.clicker.keyboard`: MediaType =
      MediaType(
        "application",
        "vnd.crick.clicker.keyboard",
        compressible = false,
        binary = true,
        fileExtensions = List("clkk")
      )

    lazy val `vnd.crick.clicker.palette`: MediaType =
      MediaType(
        "application",
        "vnd.crick.clicker.palette",
        compressible = false,
        binary = true,
        fileExtensions = List("clkp")
      )

    lazy val `vnd.crick.clicker.template`: MediaType =
      MediaType(
        "application",
        "vnd.crick.clicker.template",
        compressible = false,
        binary = true,
        fileExtensions = List("clkt")
      )

    lazy val `vnd.crick.clicker.wordbank`: MediaType =
      MediaType(
        "application",
        "vnd.crick.clicker.wordbank",
        compressible = false,
        binary = true,
        fileExtensions = List("clkw")
      )

    lazy val `vnd.criticaltools.wbs+xml`: MediaType =
      MediaType(
        "application",
        "vnd.criticaltools.wbs+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("wbs")
      )

    lazy val `vnd.cryptii.pipe+json`: MediaType =
      MediaType("application", "vnd.cryptii.pipe+json", compressible = true, binary = false)

    lazy val `vnd.crypto-shade-file`: MediaType =
      MediaType("application", "vnd.crypto-shade-file", compressible = false, binary = true)

    lazy val `vnd.cryptomator.encrypted`: MediaType =
      MediaType("application", "vnd.cryptomator.encrypted", compressible = false, binary = true)

    lazy val `vnd.cryptomator.vault`: MediaType =
      MediaType("application", "vnd.cryptomator.vault", compressible = false, binary = true)

    lazy val `vnd.ctc-posml`: MediaType =
      MediaType("application", "vnd.ctc-posml", compressible = false, binary = true, fileExtensions = List("pml"))

    lazy val `vnd.ctct.ws+xml`: MediaType =
      MediaType("application", "vnd.ctct.ws+xml", compressible = true, binary = true)

    lazy val `vnd.cups-pdf`: MediaType =
      MediaType("application", "vnd.cups-pdf", compressible = false, binary = true)

    lazy val `vnd.cups-postscript`: MediaType =
      MediaType("application", "vnd.cups-postscript", compressible = false, binary = true)

    lazy val `vnd.cups-ppd`: MediaType =
      MediaType("application", "vnd.cups-ppd", compressible = false, binary = true, fileExtensions = List("ppd"))

    lazy val `vnd.cups-raster`: MediaType =
      MediaType("application", "vnd.cups-raster", compressible = false, binary = true)

    lazy val `vnd.cups-raw`: MediaType =
      MediaType("application", "vnd.cups-raw", compressible = false, binary = true)

    lazy val `vnd.curl`: MediaType =
      MediaType("application", "vnd.curl", compressible = false, binary = true)

    lazy val `vnd.curl.car`: MediaType =
      MediaType("application", "vnd.curl.car", compressible = false, binary = true, fileExtensions = List("car"))

    lazy val `vnd.curl.pcurl`: MediaType =
      MediaType("application", "vnd.curl.pcurl", compressible = false, binary = true, fileExtensions = List("pcurl"))

    lazy val `vnd.cyan.dean.root+xml`: MediaType =
      MediaType("application", "vnd.cyan.dean.root+xml", compressible = true, binary = true)

    lazy val `vnd.cybank`: MediaType =
      MediaType("application", "vnd.cybank", compressible = false, binary = true)

    lazy val `vnd.cyclonedx+json`: MediaType =
      MediaType("application", "vnd.cyclonedx+json", compressible = true, binary = false)

    lazy val `vnd.cyclonedx+xml`: MediaType =
      MediaType("application", "vnd.cyclonedx+xml", compressible = true, binary = true)

    lazy val `vnd.d2l.coursepackage1p0+zip`: MediaType =
      MediaType("application", "vnd.d2l.coursepackage1p0+zip", compressible = false, binary = true)

    lazy val `vnd.d3m-dataset`: MediaType =
      MediaType("application", "vnd.d3m-dataset", compressible = false, binary = true)

    lazy val `vnd.d3m-problem`: MediaType =
      MediaType("application", "vnd.d3m-problem", compressible = false, binary = true)

    lazy val `vnd.dart`: MediaType =
      MediaType("application", "vnd.dart", compressible = true, binary = true, fileExtensions = List("dart"))

    lazy val `vnd.data-vision.rdz`: MediaType =
      MediaType("application", "vnd.data-vision.rdz", compressible = false, binary = true, fileExtensions = List("rdz"))

    lazy val `vnd.datalog`: MediaType =
      MediaType("application", "vnd.datalog", compressible = false, binary = true)

    lazy val `vnd.datapackage+json`: MediaType =
      MediaType("application", "vnd.datapackage+json", compressible = true, binary = false)

    lazy val `vnd.dataresource+json`: MediaType =
      MediaType("application", "vnd.dataresource+json", compressible = true, binary = false)

    lazy val `vnd.dbf`: MediaType =
      MediaType("application", "vnd.dbf", compressible = false, binary = true, fileExtensions = List("dbf"))

    lazy val `vnd.dcmp+xml`: MediaType =
      MediaType("application", "vnd.dcmp+xml", compressible = true, binary = true, fileExtensions = List("dcmp"))

    lazy val `vnd.debian.binary-package`: MediaType =
      MediaType("application", "vnd.debian.binary-package", compressible = false, binary = true)

    lazy val `vnd.dece.data`: MediaType =
      MediaType(
        "application",
        "vnd.dece.data",
        compressible = false,
        binary = true,
        fileExtensions = List("uvf", "uvvf", "uvd", "uvvd")
      )

    lazy val `vnd.dece.ttml+xml`: MediaType =
      MediaType(
        "application",
        "vnd.dece.ttml+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("uvt", "uvvt")
      )

    lazy val `vnd.dece.unspecified`: MediaType =
      MediaType(
        "application",
        "vnd.dece.unspecified",
        compressible = false,
        binary = true,
        fileExtensions = List("uvx", "uvvx")
      )

    lazy val `vnd.dece.zip`: MediaType =
      MediaType(
        "application",
        "vnd.dece.zip",
        compressible = false,
        binary = true,
        fileExtensions = List("uvz", "uvvz")
      )

    lazy val `vnd.denovo.fcselayout-link`: MediaType =
      MediaType(
        "application",
        "vnd.denovo.fcselayout-link",
        compressible = false,
        binary = true,
        fileExtensions = List("fe_launch")
      )

    lazy val `vnd.desmume.movie`: MediaType =
      MediaType("application", "vnd.desmume.movie", compressible = false, binary = true)

    lazy val `vnd.dir-bi.plate-dl-nosuffix`: MediaType =
      MediaType("application", "vnd.dir-bi.plate-dl-nosuffix", compressible = false, binary = true)

    lazy val `vnd.dm.delegation+xml`: MediaType =
      MediaType("application", "vnd.dm.delegation+xml", compressible = true, binary = true)

    lazy val `vnd.dna`: MediaType =
      MediaType("application", "vnd.dna", compressible = false, binary = true, fileExtensions = List("dna"))

    lazy val `vnd.document+json`: MediaType =
      MediaType("application", "vnd.document+json", compressible = true, binary = false)

    lazy val `vnd.dolby.mlp`: MediaType =
      MediaType("application", "vnd.dolby.mlp", compressible = false, binary = true, fileExtensions = List("mlp"))

    lazy val `vnd.dolby.mobile.1`: MediaType =
      MediaType("application", "vnd.dolby.mobile.1", compressible = false, binary = true)

    lazy val `vnd.dolby.mobile.2`: MediaType =
      MediaType("application", "vnd.dolby.mobile.2", compressible = false, binary = true)

    lazy val `vnd.doremir.scorecloud-binary-document`: MediaType =
      MediaType("application", "vnd.doremir.scorecloud-binary-document", compressible = false, binary = true)

    lazy val `vnd.dpgraph`: MediaType =
      MediaType("application", "vnd.dpgraph", compressible = false, binary = true, fileExtensions = List("dpg"))

    lazy val `vnd.dreamfactory`: MediaType =
      MediaType("application", "vnd.dreamfactory", compressible = false, binary = true, fileExtensions = List("dfac"))

    lazy val `vnd.drive+json`: MediaType =
      MediaType("application", "vnd.drive+json", compressible = true, binary = false)

    lazy val `vnd.ds-keypoint`: MediaType =
      MediaType("application", "vnd.ds-keypoint", compressible = false, binary = true, fileExtensions = List("kpxx"))

    lazy val `vnd.dtg.local`: MediaType =
      MediaType("application", "vnd.dtg.local", compressible = false, binary = true)

    lazy val `vnd.dtg.local.flash`: MediaType =
      MediaType("application", "vnd.dtg.local.flash", compressible = false, binary = true)

    lazy val `vnd.dtg.local.html`: MediaType =
      MediaType("application", "vnd.dtg.local.html", compressible = false, binary = true)

    lazy val `vnd.dvb.ait`: MediaType =
      MediaType("application", "vnd.dvb.ait", compressible = false, binary = true, fileExtensions = List("ait"))

    lazy val `vnd.dvb.dvbisl+xml`: MediaType =
      MediaType("application", "vnd.dvb.dvbisl+xml", compressible = true, binary = true)

    lazy val `vnd.dvb.dvbj`: MediaType =
      MediaType("application", "vnd.dvb.dvbj", compressible = false, binary = true)

    lazy val `vnd.dvb.esgcontainer`: MediaType =
      MediaType("application", "vnd.dvb.esgcontainer", compressible = false, binary = true)

    lazy val `vnd.dvb.ipdcdftnotifaccess`: MediaType =
      MediaType("application", "vnd.dvb.ipdcdftnotifaccess", compressible = false, binary = true)

    lazy val `vnd.dvb.ipdcesgaccess`: MediaType =
      MediaType("application", "vnd.dvb.ipdcesgaccess", compressible = false, binary = true)

    lazy val `vnd.dvb.ipdcesgaccess2`: MediaType =
      MediaType("application", "vnd.dvb.ipdcesgaccess2", compressible = false, binary = true)

    lazy val `vnd.dvb.ipdcesgpdd`: MediaType =
      MediaType("application", "vnd.dvb.ipdcesgpdd", compressible = false, binary = true)

    lazy val `vnd.dvb.ipdcroaming`: MediaType =
      MediaType("application", "vnd.dvb.ipdcroaming", compressible = false, binary = true)

    lazy val `vnd.dvb.iptv.alfec-base`: MediaType =
      MediaType("application", "vnd.dvb.iptv.alfec-base", compressible = false, binary = true)

    lazy val `vnd.dvb.iptv.alfec-enhancement`: MediaType =
      MediaType("application", "vnd.dvb.iptv.alfec-enhancement", compressible = false, binary = true)

    lazy val `vnd.dvb.notif-aggregate-root+xml`: MediaType =
      MediaType("application", "vnd.dvb.notif-aggregate-root+xml", compressible = true, binary = true)

    lazy val `vnd.dvb.notif-container+xml`: MediaType =
      MediaType("application", "vnd.dvb.notif-container+xml", compressible = true, binary = true)

    lazy val `vnd.dvb.notif-generic+xml`: MediaType =
      MediaType("application", "vnd.dvb.notif-generic+xml", compressible = true, binary = true)

    lazy val `vnd.dvb.notif-ia-msglist+xml`: MediaType =
      MediaType("application", "vnd.dvb.notif-ia-msglist+xml", compressible = true, binary = true)

    lazy val `vnd.dvb.notif-ia-registration-request+xml`: MediaType =
      MediaType("application", "vnd.dvb.notif-ia-registration-request+xml", compressible = true, binary = true)

    lazy val `vnd.dvb.notif-ia-registration-response+xml`: MediaType =
      MediaType("application", "vnd.dvb.notif-ia-registration-response+xml", compressible = true, binary = true)

    lazy val `vnd.dvb.notif-init+xml`: MediaType =
      MediaType("application", "vnd.dvb.notif-init+xml", compressible = true, binary = true)

    lazy val `vnd.dvb.pfr`: MediaType =
      MediaType("application", "vnd.dvb.pfr", compressible = false, binary = true)

    lazy val `vnd.dvb.service`: MediaType =
      MediaType("application", "vnd.dvb.service", compressible = false, binary = true, fileExtensions = List("svc"))

    lazy val `vnd.dxr`: MediaType =
      MediaType("application", "vnd.dxr", compressible = false, binary = true)

    lazy val `vnd.dynageo`: MediaType =
      MediaType("application", "vnd.dynageo", compressible = false, binary = true, fileExtensions = List("geo"))

    lazy val `vnd.dzr`: MediaType =
      MediaType("application", "vnd.dzr", compressible = false, binary = true)

    lazy val `vnd.easykaraoke.cdgdownload`: MediaType =
      MediaType("application", "vnd.easykaraoke.cdgdownload", compressible = false, binary = true)

    lazy val `vnd.ecdis-update`: MediaType =
      MediaType("application", "vnd.ecdis-update", compressible = false, binary = true)

    lazy val `vnd.ecip.rlp`: MediaType =
      MediaType("application", "vnd.ecip.rlp", compressible = false, binary = true)

    lazy val `vnd.eclipse.ditto+json`: MediaType =
      MediaType("application", "vnd.eclipse.ditto+json", compressible = true, binary = false)

    lazy val `vnd.ecowin.chart`: MediaType =
      MediaType("application", "vnd.ecowin.chart", compressible = false, binary = true, fileExtensions = List("mag"))

    lazy val `vnd.ecowin.filerequest`: MediaType =
      MediaType("application", "vnd.ecowin.filerequest", compressible = false, binary = true)

    lazy val `vnd.ecowin.fileupdate`: MediaType =
      MediaType("application", "vnd.ecowin.fileupdate", compressible = false, binary = true)

    lazy val `vnd.ecowin.series`: MediaType =
      MediaType("application", "vnd.ecowin.series", compressible = false, binary = true)

    lazy val `vnd.ecowin.seriesrequest`: MediaType =
      MediaType("application", "vnd.ecowin.seriesrequest", compressible = false, binary = true)

    lazy val `vnd.ecowin.seriesupdate`: MediaType =
      MediaType("application", "vnd.ecowin.seriesupdate", compressible = false, binary = true)

    lazy val `vnd.efi.img`: MediaType =
      MediaType("application", "vnd.efi.img", compressible = false, binary = true)

    lazy val `vnd.efi.iso`: MediaType =
      MediaType("application", "vnd.efi.iso", compressible = false, binary = true)

    lazy val `vnd.eln+zip`: MediaType =
      MediaType("application", "vnd.eln+zip", compressible = false, binary = true)

    lazy val `vnd.emclient.accessrequest+xml`: MediaType =
      MediaType("application", "vnd.emclient.accessrequest+xml", compressible = true, binary = true)

    lazy val `vnd.enliven`: MediaType =
      MediaType("application", "vnd.enliven", compressible = false, binary = true, fileExtensions = List("nml"))

    lazy val `vnd.enphase.envoy`: MediaType =
      MediaType("application", "vnd.enphase.envoy", compressible = false, binary = true)

    lazy val `vnd.eprints.data+xml`: MediaType =
      MediaType("application", "vnd.eprints.data+xml", compressible = true, binary = true)

    lazy val `vnd.epson.esf`: MediaType =
      MediaType("application", "vnd.epson.esf", compressible = false, binary = true, fileExtensions = List("esf"))

    lazy val `vnd.epson.msf`: MediaType =
      MediaType("application", "vnd.epson.msf", compressible = false, binary = true, fileExtensions = List("msf"))

    lazy val `vnd.epson.quickanime`: MediaType =
      MediaType(
        "application",
        "vnd.epson.quickanime",
        compressible = false,
        binary = true,
        fileExtensions = List("qam")
      )

    lazy val `vnd.epson.salt`: MediaType =
      MediaType("application", "vnd.epson.salt", compressible = false, binary = true, fileExtensions = List("slt"))

    lazy val `vnd.epson.ssf`: MediaType =
      MediaType("application", "vnd.epson.ssf", compressible = false, binary = true, fileExtensions = List("ssf"))

    lazy val `vnd.ericsson.quickcall`: MediaType =
      MediaType("application", "vnd.ericsson.quickcall", compressible = false, binary = true)

    lazy val `vnd.erofs`: MediaType =
      MediaType("application", "vnd.erofs", compressible = false, binary = true)

    lazy val `vnd.espass-espass+zip`: MediaType =
      MediaType("application", "vnd.espass-espass+zip", compressible = false, binary = true)

    lazy val `vnd.eszigno3+xml`: MediaType =
      MediaType(
        "application",
        "vnd.eszigno3+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("es3", "et3")
      )

    lazy val `vnd.etsi.aoc+xml`: MediaType =
      MediaType("application", "vnd.etsi.aoc+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.asic-e+zip`: MediaType =
      MediaType("application", "vnd.etsi.asic-e+zip", compressible = false, binary = true)

    lazy val `vnd.etsi.asic-s+zip`: MediaType =
      MediaType("application", "vnd.etsi.asic-s+zip", compressible = false, binary = true)

    lazy val `vnd.etsi.cug+xml`: MediaType =
      MediaType("application", "vnd.etsi.cug+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.iptvcommand+xml`: MediaType =
      MediaType("application", "vnd.etsi.iptvcommand+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.iptvdiscovery+xml`: MediaType =
      MediaType("application", "vnd.etsi.iptvdiscovery+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.iptvprofile+xml`: MediaType =
      MediaType("application", "vnd.etsi.iptvprofile+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.iptvsad-bc+xml`: MediaType =
      MediaType("application", "vnd.etsi.iptvsad-bc+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.iptvsad-cod+xml`: MediaType =
      MediaType("application", "vnd.etsi.iptvsad-cod+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.iptvsad-npvr+xml`: MediaType =
      MediaType("application", "vnd.etsi.iptvsad-npvr+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.iptvservice+xml`: MediaType =
      MediaType("application", "vnd.etsi.iptvservice+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.iptvsync+xml`: MediaType =
      MediaType("application", "vnd.etsi.iptvsync+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.iptvueprofile+xml`: MediaType =
      MediaType("application", "vnd.etsi.iptvueprofile+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.mcid+xml`: MediaType =
      MediaType("application", "vnd.etsi.mcid+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.mheg5`: MediaType =
      MediaType("application", "vnd.etsi.mheg5", compressible = false, binary = true)

    lazy val `vnd.etsi.overload-control-policy-dataset+xml`: MediaType =
      MediaType("application", "vnd.etsi.overload-control-policy-dataset+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.pstn+xml`: MediaType =
      MediaType("application", "vnd.etsi.pstn+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.sci+xml`: MediaType =
      MediaType("application", "vnd.etsi.sci+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.simservs+xml`: MediaType =
      MediaType("application", "vnd.etsi.simservs+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.timestamp-token`: MediaType =
      MediaType("application", "vnd.etsi.timestamp-token", compressible = false, binary = true)

    lazy val `vnd.etsi.tsl+xml`: MediaType =
      MediaType("application", "vnd.etsi.tsl+xml", compressible = true, binary = true)

    lazy val `vnd.etsi.tsl.der`: MediaType =
      MediaType("application", "vnd.etsi.tsl.der", compressible = false, binary = true)

    lazy val `vnd.eu.kasparian.car+json`: MediaType =
      MediaType("application", "vnd.eu.kasparian.car+json", compressible = true, binary = false)

    lazy val `vnd.eudora.data`: MediaType =
      MediaType("application", "vnd.eudora.data", compressible = false, binary = true)

    lazy val `vnd.evolv.ecig.profile`: MediaType =
      MediaType("application", "vnd.evolv.ecig.profile", compressible = false, binary = true)

    lazy val `vnd.evolv.ecig.settings`: MediaType =
      MediaType("application", "vnd.evolv.ecig.settings", compressible = false, binary = true)

    lazy val `vnd.evolv.ecig.theme`: MediaType =
      MediaType("application", "vnd.evolv.ecig.theme", compressible = false, binary = true)

    lazy val `vnd.exstream-empower+zip`: MediaType =
      MediaType("application", "vnd.exstream-empower+zip", compressible = false, binary = true)

    lazy val `vnd.exstream-package`: MediaType =
      MediaType("application", "vnd.exstream-package", compressible = false, binary = true)

    lazy val `vnd.ezpix-album`: MediaType =
      MediaType("application", "vnd.ezpix-album", compressible = false, binary = true, fileExtensions = List("ez2"))

    lazy val `vnd.ezpix-package`: MediaType =
      MediaType("application", "vnd.ezpix-package", compressible = false, binary = true, fileExtensions = List("ez3"))

    lazy val `vnd.f-secure.mobile`: MediaType =
      MediaType("application", "vnd.f-secure.mobile", compressible = false, binary = true)

    lazy val `vnd.faf+yaml`: MediaType =
      MediaType("application", "vnd.faf+yaml", compressible = false, binary = true)

    lazy val `vnd.familysearch.gedcom+zip`: MediaType =
      MediaType("application", "vnd.familysearch.gedcom+zip", compressible = false, binary = true)

    lazy val `vnd.fastcopy-disk-image`: MediaType =
      MediaType("application", "vnd.fastcopy-disk-image", compressible = false, binary = true)

    lazy val `vnd.fdf`: MediaType =
      MediaType("application", "vnd.fdf", compressible = false, binary = true, fileExtensions = List("fdf"))

    lazy val `vnd.fdsn.mseed`: MediaType =
      MediaType("application", "vnd.fdsn.mseed", compressible = false, binary = true, fileExtensions = List("mseed"))

    lazy val `vnd.fdsn.seed`: MediaType =
      MediaType(
        "application",
        "vnd.fdsn.seed",
        compressible = false,
        binary = true,
        fileExtensions = List("seed", "dataless")
      )

    lazy val `vnd.fdsn.stationxml+xml`: MediaType =
      MediaType("application", "vnd.fdsn.stationxml+xml", compressible = true, binary = true)

    lazy val `vnd.ffsns`: MediaType =
      MediaType("application", "vnd.ffsns", compressible = false, binary = true)

    lazy val `vnd.fgb`: MediaType =
      MediaType("application", "vnd.fgb", compressible = false, binary = true)

    lazy val `vnd.ficlab.flb+zip`: MediaType =
      MediaType("application", "vnd.ficlab.flb+zip", compressible = false, binary = true)

    lazy val `vnd.filmit.zfc`: MediaType =
      MediaType("application", "vnd.filmit.zfc", compressible = false, binary = true)

    lazy val `vnd.fints`: MediaType =
      MediaType("application", "vnd.fints", compressible = false, binary = true)

    lazy val `vnd.firemonkeys.cloudcell`: MediaType =
      MediaType("application", "vnd.firemonkeys.cloudcell", compressible = false, binary = true)

    lazy val `vnd.flographit`: MediaType =
      MediaType("application", "vnd.flographit", compressible = false, binary = true, fileExtensions = List("gph"))

    lazy val `vnd.fluxtime.clip`: MediaType =
      MediaType("application", "vnd.fluxtime.clip", compressible = false, binary = true, fileExtensions = List("ftc"))

    lazy val `vnd.font-fontforge-sfd`: MediaType =
      MediaType("application", "vnd.font-fontforge-sfd", compressible = false, binary = true)

    lazy val `vnd.framemaker`: MediaType =
      MediaType(
        "application",
        "vnd.framemaker",
        compressible = false,
        binary = true,
        fileExtensions = List("fm", "frame", "maker", "book")
      )

    lazy val `vnd.freelog.comic`: MediaType =
      MediaType("application", "vnd.freelog.comic", compressible = false, binary = true)

    lazy val `vnd.frogans.fnc`: MediaType =
      MediaType("application", "vnd.frogans.fnc", compressible = false, binary = true, fileExtensions = List("fnc"))

    lazy val `vnd.frogans.ltf`: MediaType =
      MediaType("application", "vnd.frogans.ltf", compressible = false, binary = true, fileExtensions = List("ltf"))

    lazy val `vnd.fsc.weblaunch`: MediaType =
      MediaType("application", "vnd.fsc.weblaunch", compressible = false, binary = true, fileExtensions = List("fsc"))

    lazy val `vnd.fujifilm.fb.docuworks`: MediaType =
      MediaType("application", "vnd.fujifilm.fb.docuworks", compressible = false, binary = true)

    lazy val `vnd.fujifilm.fb.docuworks.binder`: MediaType =
      MediaType("application", "vnd.fujifilm.fb.docuworks.binder", compressible = false, binary = true)

    lazy val `vnd.fujifilm.fb.docuworks.container`: MediaType =
      MediaType("application", "vnd.fujifilm.fb.docuworks.container", compressible = false, binary = true)

    lazy val `vnd.fujifilm.fb.jfi+xml`: MediaType =
      MediaType("application", "vnd.fujifilm.fb.jfi+xml", compressible = true, binary = true)

    lazy val `vnd.fujitsu.oasys`: MediaType =
      MediaType("application", "vnd.fujitsu.oasys", compressible = false, binary = true, fileExtensions = List("oas"))

    lazy val `vnd.fujitsu.oasys2`: MediaType =
      MediaType("application", "vnd.fujitsu.oasys2", compressible = false, binary = true, fileExtensions = List("oa2"))

    lazy val `vnd.fujitsu.oasys3`: MediaType =
      MediaType("application", "vnd.fujitsu.oasys3", compressible = false, binary = true, fileExtensions = List("oa3"))

    lazy val `vnd.fujitsu.oasysgp`: MediaType =
      MediaType("application", "vnd.fujitsu.oasysgp", compressible = false, binary = true, fileExtensions = List("fg5"))

    lazy val `vnd.fujitsu.oasysprs`: MediaType =
      MediaType(
        "application",
        "vnd.fujitsu.oasysprs",
        compressible = false,
        binary = true,
        fileExtensions = List("bh2")
      )

    lazy val `vnd.fujixerox.art-ex`: MediaType =
      MediaType("application", "vnd.fujixerox.art-ex", compressible = false, binary = true)

    lazy val `vnd.fujixerox.art4`: MediaType =
      MediaType("application", "vnd.fujixerox.art4", compressible = false, binary = true)

    lazy val `vnd.fujixerox.ddd`: MediaType =
      MediaType("application", "vnd.fujixerox.ddd", compressible = false, binary = true, fileExtensions = List("ddd"))

    lazy val `vnd.fujixerox.docuworks`: MediaType =
      MediaType(
        "application",
        "vnd.fujixerox.docuworks",
        compressible = false,
        binary = true,
        fileExtensions = List("xdw")
      )

    lazy val `vnd.fujixerox.docuworks.binder`: MediaType =
      MediaType(
        "application",
        "vnd.fujixerox.docuworks.binder",
        compressible = false,
        binary = true,
        fileExtensions = List("xbd")
      )

    lazy val `vnd.fujixerox.docuworks.container`: MediaType =
      MediaType("application", "vnd.fujixerox.docuworks.container", compressible = false, binary = true)

    lazy val `vnd.fujixerox.hbpl`: MediaType =
      MediaType("application", "vnd.fujixerox.hbpl", compressible = false, binary = true)

    lazy val `vnd.fut-misnet`: MediaType =
      MediaType("application", "vnd.fut-misnet", compressible = false, binary = true)

    lazy val `vnd.futoin+cbor`: MediaType =
      MediaType("application", "vnd.futoin+cbor", compressible = false, binary = true)

    lazy val `vnd.futoin+json`: MediaType =
      MediaType("application", "vnd.futoin+json", compressible = true, binary = false)

    lazy val `vnd.fuzzysheet`: MediaType =
      MediaType("application", "vnd.fuzzysheet", compressible = false, binary = true, fileExtensions = List("fzs"))

    lazy val `vnd.g3pix.g3fc`: MediaType =
      MediaType("application", "vnd.g3pix.g3fc", compressible = false, binary = true)

    lazy val `vnd.ga4gh.passport+jwt`: MediaType =
      MediaType("application", "vnd.ga4gh.passport+jwt", compressible = false, binary = true)

    lazy val `vnd.genomatix.tuxedo`: MediaType =
      MediaType(
        "application",
        "vnd.genomatix.tuxedo",
        compressible = false,
        binary = true,
        fileExtensions = List("txd")
      )

    lazy val `vnd.genozip`: MediaType =
      MediaType("application", "vnd.genozip", compressible = false, binary = true)

    lazy val `vnd.gentics.grd+json`: MediaType =
      MediaType("application", "vnd.gentics.grd+json", compressible = true, binary = false)

    lazy val `vnd.gentoo.catmetadata+xml`: MediaType =
      MediaType("application", "vnd.gentoo.catmetadata+xml", compressible = true, binary = true)

    lazy val `vnd.gentoo.ebuild`: MediaType =
      MediaType("application", "vnd.gentoo.ebuild", compressible = false, binary = true)

    lazy val `vnd.gentoo.eclass`: MediaType =
      MediaType("application", "vnd.gentoo.eclass", compressible = false, binary = true)

    lazy val `vnd.gentoo.gpkg`: MediaType =
      MediaType("application", "vnd.gentoo.gpkg", compressible = false, binary = true)

    lazy val `vnd.gentoo.manifest`: MediaType =
      MediaType("application", "vnd.gentoo.manifest", compressible = false, binary = true)

    lazy val `vnd.gentoo.pkgmetadata+xml`: MediaType =
      MediaType("application", "vnd.gentoo.pkgmetadata+xml", compressible = true, binary = true)

    lazy val `vnd.gentoo.xpak`: MediaType =
      MediaType("application", "vnd.gentoo.xpak", compressible = false, binary = true)

    lazy val `vnd.geo+json`: MediaType =
      MediaType("application", "vnd.geo+json", compressible = true, binary = false)

    lazy val `vnd.geocube+xml`: MediaType =
      MediaType("application", "vnd.geocube+xml", compressible = true, binary = true)

    lazy val `vnd.geogebra.file`: MediaType =
      MediaType("application", "vnd.geogebra.file", compressible = false, binary = true, fileExtensions = List("ggb"))

    lazy val `vnd.geogebra.pinboard`: MediaType =
      MediaType("application", "vnd.geogebra.pinboard", compressible = false, binary = true)

    lazy val `vnd.geogebra.slides`: MediaType =
      MediaType("application", "vnd.geogebra.slides", compressible = false, binary = true, fileExtensions = List("ggs"))

    lazy val `vnd.geogebra.tool`: MediaType =
      MediaType("application", "vnd.geogebra.tool", compressible = false, binary = true, fileExtensions = List("ggt"))

    lazy val `vnd.geometry-explorer`: MediaType =
      MediaType(
        "application",
        "vnd.geometry-explorer",
        compressible = false,
        binary = true,
        fileExtensions = List("gex", "gre")
      )

    lazy val `vnd.geonext`: MediaType =
      MediaType("application", "vnd.geonext", compressible = false, binary = true, fileExtensions = List("gxt"))

    lazy val `vnd.geoplan`: MediaType =
      MediaType("application", "vnd.geoplan", compressible = false, binary = true, fileExtensions = List("g2w"))

    lazy val `vnd.geospace`: MediaType =
      MediaType("application", "vnd.geospace", compressible = false, binary = true, fileExtensions = List("g3w"))

    lazy val `vnd.gerber`: MediaType =
      MediaType("application", "vnd.gerber", compressible = false, binary = true)

    lazy val `vnd.globalplatform.card-content-mgt`: MediaType =
      MediaType("application", "vnd.globalplatform.card-content-mgt", compressible = false, binary = true)

    lazy val `vnd.globalplatform.card-content-mgt-response`: MediaType =
      MediaType("application", "vnd.globalplatform.card-content-mgt-response", compressible = false, binary = true)

    lazy val `vnd.gmx`: MediaType =
      MediaType("application", "vnd.gmx", compressible = false, binary = true, fileExtensions = List("gmx"))

    lazy val `vnd.gnu.taler.exchange+json`: MediaType =
      MediaType("application", "vnd.gnu.taler.exchange+json", compressible = true, binary = false)

    lazy val `vnd.gnu.taler.merchant+json`: MediaType =
      MediaType("application", "vnd.gnu.taler.merchant+json", compressible = true, binary = false)

    lazy val `vnd.google-apps.audio`: MediaType =
      MediaType("application", "vnd.google-apps.audio", compressible = false, binary = true)

    lazy val `vnd.google-apps.document`: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.document",
        compressible = false,
        binary = true,
        fileExtensions = List("gdoc")
      )

    lazy val `vnd.google-apps.drawing`: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.drawing",
        compressible = false,
        binary = true,
        fileExtensions = List("gdraw")
      )

    lazy val `vnd.google-apps.drive-sdk`: MediaType =
      MediaType("application", "vnd.google-apps.drive-sdk", compressible = false, binary = true)

    lazy val `vnd.google-apps.file`: MediaType =
      MediaType("application", "vnd.google-apps.file", compressible = false, binary = true)

    lazy val `vnd.google-apps.folder`: MediaType =
      MediaType("application", "vnd.google-apps.folder", compressible = false, binary = true)

    lazy val `vnd.google-apps.form`: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.form",
        compressible = false,
        binary = true,
        fileExtensions = List("gform")
      )

    lazy val `vnd.google-apps.fusiontable`: MediaType =
      MediaType("application", "vnd.google-apps.fusiontable", compressible = false, binary = true)

    lazy val `vnd.google-apps.jam`: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.jam",
        compressible = false,
        binary = true,
        fileExtensions = List("gjam")
      )

    lazy val `vnd.google-apps.mail-layout`: MediaType =
      MediaType("application", "vnd.google-apps.mail-layout", compressible = false, binary = true)

    lazy val `vnd.google-apps.map`: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.map",
        compressible = false,
        binary = true,
        fileExtensions = List("gmap")
      )

    lazy val `vnd.google-apps.photo`: MediaType =
      MediaType("application", "vnd.google-apps.photo", compressible = false, binary = true)

    lazy val `vnd.google-apps.presentation`: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.presentation",
        compressible = false,
        binary = true,
        fileExtensions = List("gslides")
      )

    lazy val `vnd.google-apps.script`: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.script",
        compressible = false,
        binary = true,
        fileExtensions = List("gscript")
      )

    lazy val `vnd.google-apps.shortcut`: MediaType =
      MediaType("application", "vnd.google-apps.shortcut", compressible = false, binary = true)

    lazy val `vnd.google-apps.site`: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.site",
        compressible = false,
        binary = true,
        fileExtensions = List("gsite")
      )

    lazy val `vnd.google-apps.spreadsheet`: MediaType =
      MediaType(
        "application",
        "vnd.google-apps.spreadsheet",
        compressible = false,
        binary = true,
        fileExtensions = List("gsheet")
      )

    lazy val `vnd.google-apps.unknown`: MediaType =
      MediaType("application", "vnd.google-apps.unknown", compressible = false, binary = true)

    lazy val `vnd.google-apps.video`: MediaType =
      MediaType("application", "vnd.google-apps.video", compressible = false, binary = true)

    lazy val `vnd.google-earth.kml+xml`: MediaType =
      MediaType(
        "application",
        "vnd.google-earth.kml+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("kml")
      )

    lazy val `vnd.google-earth.kmz`: MediaType =
      MediaType(
        "application",
        "vnd.google-earth.kmz",
        compressible = false,
        binary = true,
        fileExtensions = List("kmz")
      )

    lazy val `vnd.gov.sk.e-form+xml`: MediaType =
      MediaType("application", "vnd.gov.sk.e-form+xml", compressible = true, binary = true)

    lazy val `vnd.gov.sk.e-form+zip`: MediaType =
      MediaType("application", "vnd.gov.sk.e-form+zip", compressible = false, binary = true)

    lazy val `vnd.gov.sk.xmldatacontainer+xml`: MediaType =
      MediaType(
        "application",
        "vnd.gov.sk.xmldatacontainer+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("xdcf")
      )

    lazy val `vnd.gpxsee.map+xml`: MediaType =
      MediaType("application", "vnd.gpxsee.map+xml", compressible = true, binary = true)

    lazy val `vnd.grafeq`: MediaType =
      MediaType("application", "vnd.grafeq", compressible = false, binary = true, fileExtensions = List("gqf", "gqs"))

    lazy val `vnd.gridmp`: MediaType =
      MediaType("application", "vnd.gridmp", compressible = false, binary = true)

    lazy val `vnd.groove-account`: MediaType =
      MediaType("application", "vnd.groove-account", compressible = false, binary = true, fileExtensions = List("gac"))

    lazy val `vnd.groove-help`: MediaType =
      MediaType("application", "vnd.groove-help", compressible = false, binary = true, fileExtensions = List("ghf"))

    lazy val `vnd.groove-identity-message`: MediaType =
      MediaType(
        "application",
        "vnd.groove-identity-message",
        compressible = false,
        binary = true,
        fileExtensions = List("gim")
      )

    lazy val `vnd.groove-injector`: MediaType =
      MediaType("application", "vnd.groove-injector", compressible = false, binary = true, fileExtensions = List("grv"))

    lazy val `vnd.groove-tool-message`: MediaType =
      MediaType(
        "application",
        "vnd.groove-tool-message",
        compressible = false,
        binary = true,
        fileExtensions = List("gtm")
      )

    lazy val `vnd.groove-tool-template`: MediaType =
      MediaType(
        "application",
        "vnd.groove-tool-template",
        compressible = false,
        binary = true,
        fileExtensions = List("tpl")
      )

    lazy val `vnd.groove-vcard`: MediaType =
      MediaType("application", "vnd.groove-vcard", compressible = false, binary = true, fileExtensions = List("vcg"))

    lazy val `vnd.hal+json`: MediaType =
      MediaType("application", "vnd.hal+json", compressible = true, binary = false)

    lazy val `vnd.hal+xml`: MediaType =
      MediaType("application", "vnd.hal+xml", compressible = true, binary = true, fileExtensions = List("hal"))

    lazy val `vnd.handheld-entertainment+xml`: MediaType =
      MediaType(
        "application",
        "vnd.handheld-entertainment+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("zmm")
      )

    lazy val `vnd.hbci`: MediaType =
      MediaType("application", "vnd.hbci", compressible = false, binary = true, fileExtensions = List("hbci"))

    lazy val `vnd.hc+json`: MediaType =
      MediaType("application", "vnd.hc+json", compressible = true, binary = false)

    lazy val `vnd.hcl-bireports`: MediaType =
      MediaType("application", "vnd.hcl-bireports", compressible = false, binary = true)

    lazy val `vnd.hdt`: MediaType =
      MediaType("application", "vnd.hdt", compressible = false, binary = true)

    lazy val `vnd.heroku+json`: MediaType =
      MediaType("application", "vnd.heroku+json", compressible = true, binary = false)

    lazy val `vnd.hhe.lesson-player`: MediaType =
      MediaType(
        "application",
        "vnd.hhe.lesson-player",
        compressible = false,
        binary = true,
        fileExtensions = List("les")
      )

    lazy val `vnd.hp-hpgl`: MediaType =
      MediaType("application", "vnd.hp-hpgl", compressible = false, binary = true, fileExtensions = List("hpgl"))

    lazy val `vnd.hp-hpid`: MediaType =
      MediaType("application", "vnd.hp-hpid", compressible = false, binary = true, fileExtensions = List("hpid"))

    lazy val `vnd.hp-hps`: MediaType =
      MediaType("application", "vnd.hp-hps", compressible = false, binary = true, fileExtensions = List("hps"))

    lazy val `vnd.hp-jlyt`: MediaType =
      MediaType("application", "vnd.hp-jlyt", compressible = false, binary = true, fileExtensions = List("jlt"))

    lazy val `vnd.hp-pcl`: MediaType =
      MediaType("application", "vnd.hp-pcl", compressible = false, binary = true, fileExtensions = List("pcl"))

    lazy val `vnd.hp-pclxl`: MediaType =
      MediaType("application", "vnd.hp-pclxl", compressible = false, binary = true, fileExtensions = List("pclxl"))

    lazy val `vnd.hsl`: MediaType =
      MediaType("application", "vnd.hsl", compressible = false, binary = true)

    lazy val `vnd.httphone`: MediaType =
      MediaType("application", "vnd.httphone", compressible = false, binary = true)

    lazy val `vnd.hydrostatix.sof-data`: MediaType =
      MediaType(
        "application",
        "vnd.hydrostatix.sof-data",
        compressible = false,
        binary = true,
        fileExtensions = List("sfd-hdstx")
      )

    lazy val `vnd.hyper+json`: MediaType =
      MediaType("application", "vnd.hyper+json", compressible = true, binary = false)

    lazy val `vnd.hyper-item+json`: MediaType =
      MediaType("application", "vnd.hyper-item+json", compressible = true, binary = false)

    lazy val `vnd.hyperdrive+json`: MediaType =
      MediaType("application", "vnd.hyperdrive+json", compressible = true, binary = false)

    lazy val `vnd.hzn-3d-crossword`: MediaType =
      MediaType("application", "vnd.hzn-3d-crossword", compressible = false, binary = true)

    lazy val `vnd.ibm.afplinedata`: MediaType =
      MediaType("application", "vnd.ibm.afplinedata", compressible = false, binary = true)

    lazy val `vnd.ibm.electronic-media`: MediaType =
      MediaType("application", "vnd.ibm.electronic-media", compressible = false, binary = true)

    lazy val `vnd.ibm.minipay`: MediaType =
      MediaType("application", "vnd.ibm.minipay", compressible = false, binary = true, fileExtensions = List("mpy"))

    lazy val `vnd.ibm.modcap`: MediaType =
      MediaType(
        "application",
        "vnd.ibm.modcap",
        compressible = false,
        binary = true,
        fileExtensions = List("afp", "listafp", "list3820")
      )

    lazy val `vnd.ibm.rights-management`: MediaType =
      MediaType(
        "application",
        "vnd.ibm.rights-management",
        compressible = false,
        binary = true,
        fileExtensions = List("irm")
      )

    lazy val `vnd.ibm.secure-container`: MediaType =
      MediaType(
        "application",
        "vnd.ibm.secure-container",
        compressible = false,
        binary = true,
        fileExtensions = List("sc")
      )

    lazy val `vnd.iccprofile`: MediaType =
      MediaType(
        "application",
        "vnd.iccprofile",
        compressible = false,
        binary = true,
        fileExtensions = List("icc", "icm")
      )

    lazy val `vnd.ieee.1905`: MediaType =
      MediaType("application", "vnd.ieee.1905", compressible = false, binary = true)

    lazy val `vnd.igloader`: MediaType =
      MediaType("application", "vnd.igloader", compressible = false, binary = true, fileExtensions = List("igl"))

    lazy val `vnd.imagemeter.folder+zip`: MediaType =
      MediaType("application", "vnd.imagemeter.folder+zip", compressible = false, binary = true)

    lazy val `vnd.imagemeter.image+zip`: MediaType =
      MediaType("application", "vnd.imagemeter.image+zip", compressible = false, binary = true)

    lazy val `vnd.immervision-ivp`: MediaType =
      MediaType("application", "vnd.immervision-ivp", compressible = false, binary = true, fileExtensions = List("ivp"))

    lazy val `vnd.immervision-ivu`: MediaType =
      MediaType("application", "vnd.immervision-ivu", compressible = false, binary = true, fileExtensions = List("ivu"))

    lazy val `vnd.ims.imsccv1p1`: MediaType =
      MediaType("application", "vnd.ims.imsccv1p1", compressible = false, binary = true)

    lazy val `vnd.ims.imsccv1p2`: MediaType =
      MediaType("application", "vnd.ims.imsccv1p2", compressible = false, binary = true)

    lazy val `vnd.ims.imsccv1p3`: MediaType =
      MediaType("application", "vnd.ims.imsccv1p3", compressible = false, binary = true)

    lazy val `vnd.ims.lis.v2.result+json`: MediaType =
      MediaType("application", "vnd.ims.lis.v2.result+json", compressible = true, binary = false)

    lazy val `vnd.ims.lti.v2.toolconsumerprofile+json`: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolconsumerprofile+json", compressible = true, binary = false)

    lazy val `vnd.ims.lti.v2.toolproxy+json`: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolproxy+json", compressible = true, binary = false)

    lazy val `vnd.ims.lti.v2.toolproxy.id+json`: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolproxy.id+json", compressible = true, binary = false)

    lazy val `vnd.ims.lti.v2.toolsettings+json`: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolsettings+json", compressible = true, binary = false)

    lazy val `vnd.ims.lti.v2.toolsettings.simple+json`: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolsettings.simple+json", compressible = true, binary = false)

    lazy val `vnd.informedcontrol.rms+xml`: MediaType =
      MediaType("application", "vnd.informedcontrol.rms+xml", compressible = true, binary = true)

    lazy val `vnd.informix-visionary`: MediaType =
      MediaType("application", "vnd.informix-visionary", compressible = false, binary = true)

    lazy val `vnd.infotech.project`: MediaType =
      MediaType("application", "vnd.infotech.project", compressible = false, binary = true)

    lazy val `vnd.infotech.project+xml`: MediaType =
      MediaType("application", "vnd.infotech.project+xml", compressible = true, binary = true)

    lazy val `vnd.innopath.wamp.notification`: MediaType =
      MediaType("application", "vnd.innopath.wamp.notification", compressible = false, binary = true)

    lazy val `vnd.insors.igm`: MediaType =
      MediaType("application", "vnd.insors.igm", compressible = false, binary = true, fileExtensions = List("igm"))

    lazy val `vnd.intercon.formnet`: MediaType =
      MediaType(
        "application",
        "vnd.intercon.formnet",
        compressible = false,
        binary = true,
        fileExtensions = List("xpw", "xpx")
      )

    lazy val `vnd.intergeo`: MediaType =
      MediaType("application", "vnd.intergeo", compressible = false, binary = true, fileExtensions = List("i2g"))

    lazy val `vnd.intertrust.digibox`: MediaType =
      MediaType("application", "vnd.intertrust.digibox", compressible = false, binary = true)

    lazy val `vnd.intertrust.nncp`: MediaType =
      MediaType("application", "vnd.intertrust.nncp", compressible = false, binary = true)

    lazy val `vnd.intu.qbo`: MediaType =
      MediaType("application", "vnd.intu.qbo", compressible = false, binary = true, fileExtensions = List("qbo"))

    lazy val `vnd.intu.qfx`: MediaType =
      MediaType("application", "vnd.intu.qfx", compressible = false, binary = true, fileExtensions = List("qfx"))

    lazy val `vnd.ipfs.ipns-record`: MediaType =
      MediaType("application", "vnd.ipfs.ipns-record", compressible = false, binary = true)

    lazy val `vnd.ipld.car`: MediaType =
      MediaType("application", "vnd.ipld.car", compressible = false, binary = true)

    lazy val `vnd.ipld.dag-cbor`: MediaType =
      MediaType("application", "vnd.ipld.dag-cbor", compressible = false, binary = true)

    lazy val `vnd.ipld.dag-json`: MediaType =
      MediaType("application", "vnd.ipld.dag-json", compressible = false, binary = false)

    lazy val `vnd.ipld.raw`: MediaType =
      MediaType("application", "vnd.ipld.raw", compressible = false, binary = true)

    lazy val `vnd.iptc.g2.catalogitem+xml`: MediaType =
      MediaType("application", "vnd.iptc.g2.catalogitem+xml", compressible = true, binary = true)

    lazy val `vnd.iptc.g2.conceptitem+xml`: MediaType =
      MediaType("application", "vnd.iptc.g2.conceptitem+xml", compressible = true, binary = true)

    lazy val `vnd.iptc.g2.knowledgeitem+xml`: MediaType =
      MediaType("application", "vnd.iptc.g2.knowledgeitem+xml", compressible = true, binary = true)

    lazy val `vnd.iptc.g2.newsitem+xml`: MediaType =
      MediaType("application", "vnd.iptc.g2.newsitem+xml", compressible = true, binary = true)

    lazy val `vnd.iptc.g2.newsmessage+xml`: MediaType =
      MediaType("application", "vnd.iptc.g2.newsmessage+xml", compressible = true, binary = true)

    lazy val `vnd.iptc.g2.packageitem+xml`: MediaType =
      MediaType("application", "vnd.iptc.g2.packageitem+xml", compressible = true, binary = true)

    lazy val `vnd.iptc.g2.planningitem+xml`: MediaType =
      MediaType("application", "vnd.iptc.g2.planningitem+xml", compressible = true, binary = true)

    lazy val `vnd.ipunplugged.rcprofile`: MediaType =
      MediaType(
        "application",
        "vnd.ipunplugged.rcprofile",
        compressible = false,
        binary = true,
        fileExtensions = List("rcprofile")
      )

    lazy val `vnd.irepository.package+xml`: MediaType =
      MediaType(
        "application",
        "vnd.irepository.package+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("irp")
      )

    lazy val `vnd.is-xpr`: MediaType =
      MediaType("application", "vnd.is-xpr", compressible = false, binary = true, fileExtensions = List("xpr"))

    lazy val `vnd.isac.fcs`: MediaType =
      MediaType("application", "vnd.isac.fcs", compressible = false, binary = true, fileExtensions = List("fcs"))

    lazy val `vnd.iso11783-10+zip`: MediaType =
      MediaType("application", "vnd.iso11783-10+zip", compressible = false, binary = true)

    lazy val `vnd.jam`: MediaType =
      MediaType("application", "vnd.jam", compressible = false, binary = true, fileExtensions = List("jam"))

    lazy val `vnd.japannet-directory-service`: MediaType =
      MediaType("application", "vnd.japannet-directory-service", compressible = false, binary = true)

    lazy val `vnd.japannet-jpnstore-wakeup`: MediaType =
      MediaType("application", "vnd.japannet-jpnstore-wakeup", compressible = false, binary = true)

    lazy val `vnd.japannet-payment-wakeup`: MediaType =
      MediaType("application", "vnd.japannet-payment-wakeup", compressible = false, binary = true)

    lazy val `vnd.japannet-registration`: MediaType =
      MediaType("application", "vnd.japannet-registration", compressible = false, binary = true)

    lazy val `vnd.japannet-registration-wakeup`: MediaType =
      MediaType("application", "vnd.japannet-registration-wakeup", compressible = false, binary = true)

    lazy val `vnd.japannet-setstore-wakeup`: MediaType =
      MediaType("application", "vnd.japannet-setstore-wakeup", compressible = false, binary = true)

    lazy val `vnd.japannet-verification`: MediaType =
      MediaType("application", "vnd.japannet-verification", compressible = false, binary = true)

    lazy val `vnd.japannet-verification-wakeup`: MediaType =
      MediaType("application", "vnd.japannet-verification-wakeup", compressible = false, binary = true)

    lazy val `vnd.jcp.javame.midlet-rms`: MediaType =
      MediaType(
        "application",
        "vnd.jcp.javame.midlet-rms",
        compressible = false,
        binary = true,
        fileExtensions = List("rms")
      )

    lazy val `vnd.jisp`: MediaType =
      MediaType("application", "vnd.jisp", compressible = false, binary = true, fileExtensions = List("jisp"))

    lazy val `vnd.joost.joda-archive`: MediaType =
      MediaType(
        "application",
        "vnd.joost.joda-archive",
        compressible = false,
        binary = true,
        fileExtensions = List("joda")
      )

    lazy val `vnd.jsk.isdn-ngn`: MediaType =
      MediaType("application", "vnd.jsk.isdn-ngn", compressible = false, binary = true)

    lazy val `vnd.kahootz`: MediaType =
      MediaType("application", "vnd.kahootz", compressible = false, binary = true, fileExtensions = List("ktz", "ktr"))

    lazy val `vnd.kde.karbon`: MediaType =
      MediaType("application", "vnd.kde.karbon", compressible = false, binary = true, fileExtensions = List("karbon"))

    lazy val `vnd.kde.kchart`: MediaType =
      MediaType("application", "vnd.kde.kchart", compressible = false, binary = true, fileExtensions = List("chrt"))

    lazy val `vnd.kde.kformula`: MediaType =
      MediaType("application", "vnd.kde.kformula", compressible = false, binary = true, fileExtensions = List("kfo"))

    lazy val `vnd.kde.kivio`: MediaType =
      MediaType("application", "vnd.kde.kivio", compressible = false, binary = true, fileExtensions = List("flw"))

    lazy val `vnd.kde.kontour`: MediaType =
      MediaType("application", "vnd.kde.kontour", compressible = false, binary = true, fileExtensions = List("kon"))

    lazy val `vnd.kde.kpresenter`: MediaType =
      MediaType(
        "application",
        "vnd.kde.kpresenter",
        compressible = false,
        binary = true,
        fileExtensions = List("kpr", "kpt")
      )

    lazy val `vnd.kde.kspread`: MediaType =
      MediaType("application", "vnd.kde.kspread", compressible = false, binary = true, fileExtensions = List("ksp"))

    lazy val `vnd.kde.kword`: MediaType =
      MediaType(
        "application",
        "vnd.kde.kword",
        compressible = false,
        binary = true,
        fileExtensions = List("kwd", "kwt")
      )

    lazy val `vnd.kdl`: MediaType =
      MediaType("application", "vnd.kdl", compressible = false, binary = true)

    lazy val `vnd.kenameaapp`: MediaType =
      MediaType("application", "vnd.kenameaapp", compressible = false, binary = true, fileExtensions = List("htke"))

    lazy val `vnd.keyman.kmp+zip`: MediaType =
      MediaType("application", "vnd.keyman.kmp+zip", compressible = false, binary = true)

    lazy val `vnd.keyman.kmx`: MediaType =
      MediaType("application", "vnd.keyman.kmx", compressible = false, binary = true)

    lazy val `vnd.kidspiration`: MediaType =
      MediaType("application", "vnd.kidspiration", compressible = false, binary = true, fileExtensions = List("kia"))

    lazy val `vnd.kinar`: MediaType =
      MediaType("application", "vnd.kinar", compressible = false, binary = true, fileExtensions = List("kne", "knp"))

    lazy val `vnd.koan`: MediaType =
      MediaType(
        "application",
        "vnd.koan",
        compressible = false,
        binary = true,
        fileExtensions = List("skp", "skd", "skt", "skm")
      )

    lazy val `vnd.kodak-descriptor`: MediaType =
      MediaType(
        "application",
        "vnd.kodak-descriptor",
        compressible = false,
        binary = true,
        fileExtensions = List("sse")
      )

    lazy val `vnd.las`: MediaType =
      MediaType("application", "vnd.las", compressible = false, binary = true)

    lazy val `vnd.las.las+json`: MediaType =
      MediaType("application", "vnd.las.las+json", compressible = true, binary = false)

    lazy val `vnd.las.las+xml`: MediaType =
      MediaType("application", "vnd.las.las+xml", compressible = true, binary = true, fileExtensions = List("lasxml"))

    lazy val `vnd.laszip`: MediaType =
      MediaType("application", "vnd.laszip", compressible = false, binary = true)

    lazy val `vnd.ldev.productlicensing`: MediaType =
      MediaType("application", "vnd.ldev.productlicensing", compressible = false, binary = true)

    lazy val `vnd.leap+json`: MediaType =
      MediaType("application", "vnd.leap+json", compressible = true, binary = false)

    lazy val `vnd.liberty-request+xml`: MediaType =
      MediaType("application", "vnd.liberty-request+xml", compressible = true, binary = true)

    lazy val `vnd.llamagraphics.life-balance.desktop`: MediaType =
      MediaType(
        "application",
        "vnd.llamagraphics.life-balance.desktop",
        compressible = false,
        binary = true,
        fileExtensions = List("lbd")
      )

    lazy val `vnd.llamagraphics.life-balance.exchange+xml`: MediaType =
      MediaType(
        "application",
        "vnd.llamagraphics.life-balance.exchange+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("lbe")
      )

    lazy val `vnd.logipipe.circuit+zip`: MediaType =
      MediaType("application", "vnd.logipipe.circuit+zip", compressible = false, binary = true)

    lazy val `vnd.loom`: MediaType =
      MediaType("application", "vnd.loom", compressible = false, binary = true)

    lazy val `vnd.lotus-1-2-3`: MediaType =
      MediaType("application", "vnd.lotus-1-2-3", compressible = false, binary = true, fileExtensions = List("123"))

    lazy val `vnd.lotus-approach`: MediaType =
      MediaType("application", "vnd.lotus-approach", compressible = false, binary = true, fileExtensions = List("apr"))

    lazy val `vnd.lotus-freelance`: MediaType =
      MediaType("application", "vnd.lotus-freelance", compressible = false, binary = true, fileExtensions = List("pre"))

    lazy val `vnd.lotus-notes`: MediaType =
      MediaType("application", "vnd.lotus-notes", compressible = false, binary = true, fileExtensions = List("nsf"))

    lazy val `vnd.lotus-organizer`: MediaType =
      MediaType("application", "vnd.lotus-organizer", compressible = false, binary = true, fileExtensions = List("org"))

    lazy val `vnd.lotus-screencam`: MediaType =
      MediaType("application", "vnd.lotus-screencam", compressible = false, binary = true, fileExtensions = List("scm"))

    lazy val `vnd.lotus-wordpro`: MediaType =
      MediaType("application", "vnd.lotus-wordpro", compressible = false, binary = true, fileExtensions = List("lwp"))

    lazy val `vnd.macports.portpkg`: MediaType =
      MediaType(
        "application",
        "vnd.macports.portpkg",
        compressible = false,
        binary = true,
        fileExtensions = List("portpkg")
      )

    lazy val `vnd.maml`: MediaType =
      MediaType("application", "vnd.maml", compressible = false, binary = true)

    lazy val `vnd.mapbox-vector-tile`: MediaType =
      MediaType(
        "application",
        "vnd.mapbox-vector-tile",
        compressible = false,
        binary = true,
        fileExtensions = List("mvt")
      )

    lazy val `vnd.marlin.drm.actiontoken+xml`: MediaType =
      MediaType("application", "vnd.marlin.drm.actiontoken+xml", compressible = true, binary = true)

    lazy val `vnd.marlin.drm.conftoken+xml`: MediaType =
      MediaType("application", "vnd.marlin.drm.conftoken+xml", compressible = true, binary = true)

    lazy val `vnd.marlin.drm.license+xml`: MediaType =
      MediaType("application", "vnd.marlin.drm.license+xml", compressible = true, binary = true)

    lazy val `vnd.marlin.drm.mdcf`: MediaType =
      MediaType("application", "vnd.marlin.drm.mdcf", compressible = false, binary = true)

    lazy val `vnd.mason+json`: MediaType =
      MediaType("application", "vnd.mason+json", compressible = true, binary = false)

    lazy val `vnd.maxar.archive.3tz+zip`: MediaType =
      MediaType("application", "vnd.maxar.archive.3tz+zip", compressible = false, binary = true)

    lazy val `vnd.maxmind.maxmind-db`: MediaType =
      MediaType("application", "vnd.maxmind.maxmind-db", compressible = false, binary = true)

    lazy val `vnd.mcd`: MediaType =
      MediaType("application", "vnd.mcd", compressible = false, binary = true, fileExtensions = List("mcd"))

    lazy val `vnd.mdl`: MediaType =
      MediaType("application", "vnd.mdl", compressible = false, binary = true)

    lazy val `vnd.mdl-mbsdf`: MediaType =
      MediaType("application", "vnd.mdl-mbsdf", compressible = false, binary = true)

    lazy val `vnd.medcalcdata`: MediaType =
      MediaType("application", "vnd.medcalcdata", compressible = false, binary = true, fileExtensions = List("mc1"))

    lazy val `vnd.mediastation.cdkey`: MediaType =
      MediaType(
        "application",
        "vnd.mediastation.cdkey",
        compressible = false,
        binary = true,
        fileExtensions = List("cdkey")
      )

    lazy val `vnd.medicalholodeck.recordxr`: MediaType =
      MediaType("application", "vnd.medicalholodeck.recordxr", compressible = false, binary = true)

    lazy val `vnd.meridian-slingshot`: MediaType =
      MediaType("application", "vnd.meridian-slingshot", compressible = false, binary = true)

    lazy val `vnd.mermaid`: MediaType =
      MediaType("application", "vnd.mermaid", compressible = false, binary = true)

    lazy val `vnd.mfer`: MediaType =
      MediaType("application", "vnd.mfer", compressible = false, binary = true, fileExtensions = List("mwf"))

    lazy val `vnd.mfmp`: MediaType =
      MediaType("application", "vnd.mfmp", compressible = false, binary = true, fileExtensions = List("mfm"))

    lazy val `vnd.micro+json`: MediaType =
      MediaType("application", "vnd.micro+json", compressible = true, binary = false)

    lazy val `vnd.micrografx.flo`: MediaType =
      MediaType("application", "vnd.micrografx.flo", compressible = false, binary = true, fileExtensions = List("flo"))

    lazy val `vnd.micrografx.igx`: MediaType =
      MediaType("application", "vnd.micrografx.igx", compressible = false, binary = true, fileExtensions = List("igx"))

    lazy val `vnd.microsoft.portable-executable`: MediaType =
      MediaType("application", "vnd.microsoft.portable-executable", compressible = false, binary = true)

    lazy val `vnd.microsoft.windows.thumbnail-cache`: MediaType =
      MediaType("application", "vnd.microsoft.windows.thumbnail-cache", compressible = false, binary = true)

    lazy val `vnd.miele+json`: MediaType =
      MediaType("application", "vnd.miele+json", compressible = true, binary = false)

    lazy val `vnd.mif`: MediaType =
      MediaType("application", "vnd.mif", compressible = false, binary = true, fileExtensions = List("mif"))

    lazy val `vnd.minisoft-hp3000-save`: MediaType =
      MediaType("application", "vnd.minisoft-hp3000-save", compressible = false, binary = true)

    lazy val `vnd.mitsubishi.misty-guard.trustweb`: MediaType =
      MediaType("application", "vnd.mitsubishi.misty-guard.trustweb", compressible = false, binary = true)

    lazy val `vnd.mobius.daf`: MediaType =
      MediaType("application", "vnd.mobius.daf", compressible = false, binary = true, fileExtensions = List("daf"))

    lazy val `vnd.mobius.dis`: MediaType =
      MediaType("application", "vnd.mobius.dis", compressible = false, binary = true, fileExtensions = List("dis"))

    lazy val `vnd.mobius.mbk`: MediaType =
      MediaType("application", "vnd.mobius.mbk", compressible = false, binary = true, fileExtensions = List("mbk"))

    lazy val `vnd.mobius.mqy`: MediaType =
      MediaType("application", "vnd.mobius.mqy", compressible = false, binary = true, fileExtensions = List("mqy"))

    lazy val `vnd.mobius.msl`: MediaType =
      MediaType("application", "vnd.mobius.msl", compressible = false, binary = true, fileExtensions = List("msl"))

    lazy val `vnd.mobius.plc`: MediaType =
      MediaType("application", "vnd.mobius.plc", compressible = false, binary = true, fileExtensions = List("plc"))

    lazy val `vnd.mobius.txf`: MediaType =
      MediaType("application", "vnd.mobius.txf", compressible = false, binary = true, fileExtensions = List("txf"))

    lazy val `vnd.modl`: MediaType =
      MediaType("application", "vnd.modl", compressible = false, binary = true)

    lazy val `vnd.mophun.application`: MediaType =
      MediaType(
        "application",
        "vnd.mophun.application",
        compressible = false,
        binary = true,
        fileExtensions = List("mpn")
      )

    lazy val `vnd.mophun.certificate`: MediaType =
      MediaType(
        "application",
        "vnd.mophun.certificate",
        compressible = false,
        binary = true,
        fileExtensions = List("mpc")
      )

    lazy val `vnd.motorola.flexsuite`: MediaType =
      MediaType("application", "vnd.motorola.flexsuite", compressible = false, binary = true)

    lazy val `vnd.motorola.flexsuite.adsi`: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.adsi", compressible = false, binary = true)

    lazy val `vnd.motorola.flexsuite.fis`: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.fis", compressible = false, binary = true)

    lazy val `vnd.motorola.flexsuite.gotap`: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.gotap", compressible = false, binary = true)

    lazy val `vnd.motorola.flexsuite.kmr`: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.kmr", compressible = false, binary = true)

    lazy val `vnd.motorola.flexsuite.ttc`: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.ttc", compressible = false, binary = true)

    lazy val `vnd.motorola.flexsuite.wem`: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.wem", compressible = false, binary = true)

    lazy val `vnd.motorola.iprm`: MediaType =
      MediaType("application", "vnd.motorola.iprm", compressible = false, binary = true)

    lazy val `vnd.mozilla.xul+xml`: MediaType =
      MediaType("application", "vnd.mozilla.xul+xml", compressible = true, binary = true, fileExtensions = List("xul"))

    lazy val `vnd.ms-3mfdocument`: MediaType =
      MediaType("application", "vnd.ms-3mfdocument", compressible = false, binary = true)

    lazy val `vnd.ms-artgalry`: MediaType =
      MediaType("application", "vnd.ms-artgalry", compressible = false, binary = true, fileExtensions = List("cil"))

    lazy val `vnd.ms-asf`: MediaType =
      MediaType("application", "vnd.ms-asf", compressible = false, binary = true)

    lazy val `vnd.ms-cab-compressed`: MediaType =
      MediaType(
        "application",
        "vnd.ms-cab-compressed",
        compressible = false,
        binary = true,
        fileExtensions = List("cab")
      )

    lazy val `vnd.ms-color.iccprofile`: MediaType =
      MediaType("application", "vnd.ms-color.iccprofile", compressible = false, binary = true)

    lazy val `vnd.ms-excel`: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel",
        compressible = false,
        binary = true,
        fileExtensions = List("xls", "xlm", "xla", "xlc", "xlt", "xlw")
      )

    lazy val `vnd.ms-excel.addin.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel.addin.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("xlam")
      )

    lazy val `vnd.ms-excel.sheet.binary.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel.sheet.binary.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("xlsb")
      )

    lazy val `vnd.ms-excel.sheet.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel.sheet.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("xlsm")
      )

    lazy val `vnd.ms-excel.template.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-excel.template.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("xltm")
      )

    lazy val `vnd.ms-fontobject`: MediaType =
      MediaType("application", "vnd.ms-fontobject", compressible = true, binary = true, fileExtensions = List("eot"))

    lazy val `vnd.ms-htmlhelp`: MediaType =
      MediaType("application", "vnd.ms-htmlhelp", compressible = false, binary = true, fileExtensions = List("chm"))

    lazy val `vnd.ms-ims`: MediaType =
      MediaType("application", "vnd.ms-ims", compressible = false, binary = true, fileExtensions = List("ims"))

    lazy val `vnd.ms-lrm`: MediaType =
      MediaType("application", "vnd.ms-lrm", compressible = false, binary = true, fileExtensions = List("lrm"))

    lazy val `vnd.ms-office.activex+xml`: MediaType =
      MediaType("application", "vnd.ms-office.activex+xml", compressible = true, binary = true)

    lazy val `vnd.ms-officetheme`: MediaType =
      MediaType("application", "vnd.ms-officetheme", compressible = false, binary = true, fileExtensions = List("thmx"))

    lazy val `vnd.ms-opentype`: MediaType =
      MediaType("application", "vnd.ms-opentype", compressible = true, binary = true)

    lazy val `vnd.ms-outlook`: MediaType =
      MediaType("application", "vnd.ms-outlook", compressible = false, binary = true, fileExtensions = List("msg"))

    lazy val `vnd.ms-package.obfuscated-opentype`: MediaType =
      MediaType("application", "vnd.ms-package.obfuscated-opentype", compressible = false, binary = true)

    lazy val `vnd.ms-pki.seccat`: MediaType =
      MediaType("application", "vnd.ms-pki.seccat", compressible = false, binary = true, fileExtensions = List("cat"))

    lazy val `vnd.ms-pki.stl`: MediaType =
      MediaType("application", "vnd.ms-pki.stl", compressible = false, binary = true, fileExtensions = List("stl"))

    lazy val `vnd.ms-playready.initiator+xml`: MediaType =
      MediaType("application", "vnd.ms-playready.initiator+xml", compressible = true, binary = true)

    lazy val `vnd.ms-powerpoint`: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint",
        compressible = false,
        binary = true,
        fileExtensions = List("ppt", "pps", "pot")
      )

    lazy val `vnd.ms-powerpoint.addin.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.addin.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("ppam")
      )

    lazy val `vnd.ms-powerpoint.presentation.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.presentation.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("pptm")
      )

    lazy val `vnd.ms-powerpoint.slide.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.slide.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("sldm")
      )

    lazy val `vnd.ms-powerpoint.slideshow.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.slideshow.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("ppsm")
      )

    lazy val `vnd.ms-powerpoint.template.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-powerpoint.template.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("potm")
      )

    lazy val `vnd.ms-printdevicecapabilities+xml`: MediaType =
      MediaType("application", "vnd.ms-printdevicecapabilities+xml", compressible = true, binary = true)

    lazy val `vnd.ms-printing.printticket+xml`: MediaType =
      MediaType("application", "vnd.ms-printing.printticket+xml", compressible = true, binary = true)

    lazy val `vnd.ms-printschematicket+xml`: MediaType =
      MediaType("application", "vnd.ms-printschematicket+xml", compressible = true, binary = true)

    lazy val `vnd.ms-project`: MediaType =
      MediaType(
        "application",
        "vnd.ms-project",
        compressible = false,
        binary = true,
        fileExtensions = List("mpp", "mpt")
      )

    lazy val `vnd.ms-tnef`: MediaType =
      MediaType("application", "vnd.ms-tnef", compressible = false, binary = true)

    lazy val `vnd.ms-visio.viewer`: MediaType =
      MediaType("application", "vnd.ms-visio.viewer", compressible = false, binary = true, fileExtensions = List("vdx"))

    lazy val `vnd.ms-windows.devicepairing`: MediaType =
      MediaType("application", "vnd.ms-windows.devicepairing", compressible = false, binary = true)

    lazy val `vnd.ms-windows.nwprinting.oob`: MediaType =
      MediaType("application", "vnd.ms-windows.nwprinting.oob", compressible = false, binary = true)

    lazy val `vnd.ms-windows.printerpairing`: MediaType =
      MediaType("application", "vnd.ms-windows.printerpairing", compressible = false, binary = true)

    lazy val `vnd.ms-windows.wsd.oob`: MediaType =
      MediaType("application", "vnd.ms-windows.wsd.oob", compressible = false, binary = true)

    lazy val `vnd.ms-wmdrm.lic-chlg-req`: MediaType =
      MediaType("application", "vnd.ms-wmdrm.lic-chlg-req", compressible = false, binary = true)

    lazy val `vnd.ms-wmdrm.lic-resp`: MediaType =
      MediaType("application", "vnd.ms-wmdrm.lic-resp", compressible = false, binary = true)

    lazy val `vnd.ms-wmdrm.meter-chlg-req`: MediaType =
      MediaType("application", "vnd.ms-wmdrm.meter-chlg-req", compressible = false, binary = true)

    lazy val `vnd.ms-wmdrm.meter-resp`: MediaType =
      MediaType("application", "vnd.ms-wmdrm.meter-resp", compressible = false, binary = true)

    lazy val `vnd.ms-word.document.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-word.document.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("docm")
      )

    lazy val `vnd.ms-word.template.macroenabled.12`: MediaType =
      MediaType(
        "application",
        "vnd.ms-word.template.macroenabled.12",
        compressible = false,
        binary = true,
        fileExtensions = List("dotm")
      )

    lazy val `vnd.ms-works`: MediaType =
      MediaType(
        "application",
        "vnd.ms-works",
        compressible = false,
        binary = true,
        fileExtensions = List("wps", "wks", "wcm", "wdb")
      )

    lazy val `vnd.ms-wpl`: MediaType =
      MediaType("application", "vnd.ms-wpl", compressible = false, binary = true, fileExtensions = List("wpl"))

    lazy val `vnd.ms-xpsdocument`: MediaType =
      MediaType("application", "vnd.ms-xpsdocument", compressible = false, binary = true, fileExtensions = List("xps"))

    lazy val `vnd.msa-disk-image`: MediaType =
      MediaType("application", "vnd.msa-disk-image", compressible = false, binary = true)

    lazy val `vnd.mseq`: MediaType =
      MediaType("application", "vnd.mseq", compressible = false, binary = true, fileExtensions = List("mseq"))

    lazy val `vnd.msgpack`: MediaType =
      MediaType("application", "vnd.msgpack", compressible = false, binary = true)

    lazy val `vnd.msign`: MediaType =
      MediaType("application", "vnd.msign", compressible = false, binary = true)

    lazy val `vnd.multiad.creator`: MediaType =
      MediaType("application", "vnd.multiad.creator", compressible = false, binary = true)

    lazy val `vnd.multiad.creator.cif`: MediaType =
      MediaType("application", "vnd.multiad.creator.cif", compressible = false, binary = true)

    lazy val `vnd.music-niff`: MediaType =
      MediaType("application", "vnd.music-niff", compressible = false, binary = true)

    lazy val `vnd.musician`: MediaType =
      MediaType("application", "vnd.musician", compressible = false, binary = true, fileExtensions = List("mus"))

    lazy val `vnd.muvee.style`: MediaType =
      MediaType("application", "vnd.muvee.style", compressible = false, binary = true, fileExtensions = List("msty"))

    lazy val `vnd.mynfc`: MediaType =
      MediaType("application", "vnd.mynfc", compressible = false, binary = true, fileExtensions = List("taglet"))

    lazy val `vnd.nacamar.ybrid+json`: MediaType =
      MediaType("application", "vnd.nacamar.ybrid+json", compressible = true, binary = false)

    lazy val `vnd.nato.bindingdataobject+cbor`: MediaType =
      MediaType("application", "vnd.nato.bindingdataobject+cbor", compressible = false, binary = true)

    lazy val `vnd.nato.bindingdataobject+json`: MediaType =
      MediaType("application", "vnd.nato.bindingdataobject+json", compressible = true, binary = false)

    lazy val `vnd.nato.bindingdataobject+xml`: MediaType =
      MediaType(
        "application",
        "vnd.nato.bindingdataobject+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("bdo")
      )

    lazy val `vnd.nato.openxmlformats-package.iepd+zip`: MediaType =
      MediaType("application", "vnd.nato.openxmlformats-package.iepd+zip", compressible = false, binary = true)

    lazy val `vnd.ncd.control`: MediaType =
      MediaType("application", "vnd.ncd.control", compressible = false, binary = true)

    lazy val `vnd.ncd.reference`: MediaType =
      MediaType("application", "vnd.ncd.reference", compressible = false, binary = true)

    lazy val `vnd.nearst.inv+json`: MediaType =
      MediaType("application", "vnd.nearst.inv+json", compressible = true, binary = false)

    lazy val `vnd.nebumind.line`: MediaType =
      MediaType("application", "vnd.nebumind.line", compressible = false, binary = true)

    lazy val `vnd.nervana`: MediaType =
      MediaType("application", "vnd.nervana", compressible = false, binary = true)

    lazy val `vnd.netfpx`: MediaType =
      MediaType("application", "vnd.netfpx", compressible = false, binary = true)

    lazy val `vnd.neurolanguage.nlu`: MediaType =
      MediaType(
        "application",
        "vnd.neurolanguage.nlu",
        compressible = false,
        binary = true,
        fileExtensions = List("nlu")
      )

    lazy val `vnd.nimn`: MediaType =
      MediaType("application", "vnd.nimn", compressible = false, binary = true)

    lazy val `vnd.nintendo.nitro.rom`: MediaType =
      MediaType("application", "vnd.nintendo.nitro.rom", compressible = false, binary = true)

    lazy val `vnd.nintendo.snes.rom`: MediaType =
      MediaType("application", "vnd.nintendo.snes.rom", compressible = false, binary = true)

    lazy val `vnd.nitf`: MediaType =
      MediaType("application", "vnd.nitf", compressible = false, binary = true, fileExtensions = List("ntf", "nitf"))

    lazy val `vnd.noblenet-directory`: MediaType =
      MediaType(
        "application",
        "vnd.noblenet-directory",
        compressible = false,
        binary = true,
        fileExtensions = List("nnd")
      )

    lazy val `vnd.noblenet-sealer`: MediaType =
      MediaType("application", "vnd.noblenet-sealer", compressible = false, binary = true, fileExtensions = List("nns"))

    lazy val `vnd.noblenet-web`: MediaType =
      MediaType("application", "vnd.noblenet-web", compressible = false, binary = true, fileExtensions = List("nnw"))

    lazy val `vnd.nokia.catalogs`: MediaType =
      MediaType("application", "vnd.nokia.catalogs", compressible = false, binary = true)

    lazy val `vnd.nokia.conml+wbxml`: MediaType =
      MediaType("application", "vnd.nokia.conml+wbxml", compressible = false, binary = true)

    lazy val `vnd.nokia.conml+xml`: MediaType =
      MediaType("application", "vnd.nokia.conml+xml", compressible = true, binary = true)

    lazy val `vnd.nokia.iptv.config+xml`: MediaType =
      MediaType("application", "vnd.nokia.iptv.config+xml", compressible = true, binary = true)

    lazy val `vnd.nokia.isds-radio-presets`: MediaType =
      MediaType("application", "vnd.nokia.isds-radio-presets", compressible = false, binary = true)

    lazy val `vnd.nokia.landmark+wbxml`: MediaType =
      MediaType("application", "vnd.nokia.landmark+wbxml", compressible = false, binary = true)

    lazy val `vnd.nokia.landmark+xml`: MediaType =
      MediaType("application", "vnd.nokia.landmark+xml", compressible = true, binary = true)

    lazy val `vnd.nokia.landmarkcollection+xml`: MediaType =
      MediaType("application", "vnd.nokia.landmarkcollection+xml", compressible = true, binary = true)

    lazy val `vnd.nokia.n-gage.ac+xml`: MediaType =
      MediaType(
        "application",
        "vnd.nokia.n-gage.ac+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("ac")
      )

    lazy val `vnd.nokia.n-gage.data`: MediaType =
      MediaType(
        "application",
        "vnd.nokia.n-gage.data",
        compressible = false,
        binary = true,
        fileExtensions = List("ngdat")
      )

    lazy val `vnd.nokia.n-gage.symbian.install`: MediaType =
      MediaType(
        "application",
        "vnd.nokia.n-gage.symbian.install",
        compressible = false,
        binary = true,
        fileExtensions = List("n-gage")
      )

    lazy val `vnd.nokia.ncd`: MediaType =
      MediaType("application", "vnd.nokia.ncd", compressible = false, binary = true)

    lazy val `vnd.nokia.pcd+wbxml`: MediaType =
      MediaType("application", "vnd.nokia.pcd+wbxml", compressible = false, binary = true)

    lazy val `vnd.nokia.pcd+xml`: MediaType =
      MediaType("application", "vnd.nokia.pcd+xml", compressible = true, binary = true)

    lazy val `vnd.nokia.radio-preset`: MediaType =
      MediaType(
        "application",
        "vnd.nokia.radio-preset",
        compressible = false,
        binary = true,
        fileExtensions = List("rpst")
      )

    lazy val `vnd.nokia.radio-presets`: MediaType =
      MediaType(
        "application",
        "vnd.nokia.radio-presets",
        compressible = false,
        binary = true,
        fileExtensions = List("rpss")
      )

    lazy val `vnd.novadigm.edm`: MediaType =
      MediaType("application", "vnd.novadigm.edm", compressible = false, binary = true, fileExtensions = List("edm"))

    lazy val `vnd.novadigm.edx`: MediaType =
      MediaType("application", "vnd.novadigm.edx", compressible = false, binary = true, fileExtensions = List("edx"))

    lazy val `vnd.novadigm.ext`: MediaType =
      MediaType("application", "vnd.novadigm.ext", compressible = false, binary = true, fileExtensions = List("ext"))

    lazy val `vnd.ntt-local.content-share`: MediaType =
      MediaType("application", "vnd.ntt-local.content-share", compressible = false, binary = true)

    lazy val `vnd.ntt-local.file-transfer`: MediaType =
      MediaType("application", "vnd.ntt-local.file-transfer", compressible = false, binary = true)

    lazy val `vnd.ntt-local.ogw_remote-access`: MediaType =
      MediaType("application", "vnd.ntt-local.ogw_remote-access", compressible = false, binary = true)

    lazy val `vnd.ntt-local.sip-ta_remote`: MediaType =
      MediaType("application", "vnd.ntt-local.sip-ta_remote", compressible = false, binary = true)

    lazy val `vnd.ntt-local.sip-ta_tcp_stream`: MediaType =
      MediaType("application", "vnd.ntt-local.sip-ta_tcp_stream", compressible = false, binary = true)

    lazy val `vnd.nubaltec.nudoku-game`: MediaType =
      MediaType("application", "vnd.nubaltec.nudoku-game", compressible = false, binary = true)

    lazy val `vnd.oai.workflows`: MediaType =
      MediaType("application", "vnd.oai.workflows", compressible = false, binary = true)

    lazy val `vnd.oai.workflows+json`: MediaType =
      MediaType("application", "vnd.oai.workflows+json", compressible = true, binary = false)

    lazy val `vnd.oai.workflows+yaml`: MediaType =
      MediaType("application", "vnd.oai.workflows+yaml", compressible = false, binary = true)

    lazy val `vnd.oasis.opendocument.base`: MediaType =
      MediaType("application", "vnd.oasis.opendocument.base", compressible = false, binary = true)

    lazy val `vnd.oasis.opendocument.chart`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.chart",
        compressible = false,
        binary = true,
        fileExtensions = List("odc")
      )

    lazy val `vnd.oasis.opendocument.chart-template`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.chart-template",
        compressible = false,
        binary = true,
        fileExtensions = List("otc")
      )

    lazy val `vnd.oasis.opendocument.database`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.database",
        compressible = false,
        binary = true,
        fileExtensions = List("odb")
      )

    lazy val `vnd.oasis.opendocument.formula`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.formula",
        compressible = false,
        binary = true,
        fileExtensions = List("odf")
      )

    lazy val `vnd.oasis.opendocument.formula-template`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.formula-template",
        compressible = false,
        binary = true,
        fileExtensions = List("odft")
      )

    lazy val `vnd.oasis.opendocument.graphics`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.graphics",
        compressible = false,
        binary = true,
        fileExtensions = List("odg")
      )

    lazy val `vnd.oasis.opendocument.graphics-template`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.graphics-template",
        compressible = false,
        binary = true,
        fileExtensions = List("otg")
      )

    lazy val `vnd.oasis.opendocument.image`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.image",
        compressible = false,
        binary = true,
        fileExtensions = List("odi")
      )

    lazy val `vnd.oasis.opendocument.image-template`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.image-template",
        compressible = false,
        binary = true,
        fileExtensions = List("oti")
      )

    lazy val `vnd.oasis.opendocument.presentation`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.presentation",
        compressible = false,
        binary = true,
        fileExtensions = List("odp")
      )

    lazy val `vnd.oasis.opendocument.presentation-template`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.presentation-template",
        compressible = false,
        binary = true,
        fileExtensions = List("otp")
      )

    lazy val `vnd.oasis.opendocument.spreadsheet`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.spreadsheet",
        compressible = false,
        binary = true,
        fileExtensions = List("ods")
      )

    lazy val `vnd.oasis.opendocument.spreadsheet-template`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.spreadsheet-template",
        compressible = false,
        binary = true,
        fileExtensions = List("ots")
      )

    lazy val `vnd.oasis.opendocument.text`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.text",
        compressible = false,
        binary = true,
        fileExtensions = List("odt")
      )

    lazy val `vnd.oasis.opendocument.text-master`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.text-master",
        compressible = false,
        binary = true,
        fileExtensions = List("odm")
      )

    lazy val `vnd.oasis.opendocument.text-master-template`: MediaType =
      MediaType("application", "vnd.oasis.opendocument.text-master-template", compressible = false, binary = true)

    lazy val `vnd.oasis.opendocument.text-template`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.text-template",
        compressible = false,
        binary = true,
        fileExtensions = List("ott")
      )

    lazy val `vnd.oasis.opendocument.text-web`: MediaType =
      MediaType(
        "application",
        "vnd.oasis.opendocument.text-web",
        compressible = false,
        binary = true,
        fileExtensions = List("oth")
      )

    lazy val `vnd.obn`: MediaType =
      MediaType("application", "vnd.obn", compressible = false, binary = true)

    lazy val `vnd.ocf+cbor`: MediaType =
      MediaType("application", "vnd.ocf+cbor", compressible = false, binary = true)

    lazy val `vnd.oci.image.manifest.v1+json`: MediaType =
      MediaType("application", "vnd.oci.image.manifest.v1+json", compressible = true, binary = false)

    lazy val `vnd.oftn.l10n+json`: MediaType =
      MediaType("application", "vnd.oftn.l10n+json", compressible = true, binary = false)

    lazy val `vnd.oipf.contentaccessdownload+xml`: MediaType =
      MediaType("application", "vnd.oipf.contentaccessdownload+xml", compressible = true, binary = true)

    lazy val `vnd.oipf.contentaccessstreaming+xml`: MediaType =
      MediaType("application", "vnd.oipf.contentaccessstreaming+xml", compressible = true, binary = true)

    lazy val `vnd.oipf.cspg-hexbinary`: MediaType =
      MediaType("application", "vnd.oipf.cspg-hexbinary", compressible = false, binary = true)

    lazy val `vnd.oipf.dae.svg+xml`: MediaType =
      MediaType("application", "vnd.oipf.dae.svg+xml", compressible = true, binary = true)

    lazy val `vnd.oipf.dae.xhtml+xml`: MediaType =
      MediaType("application", "vnd.oipf.dae.xhtml+xml", compressible = true, binary = true)

    lazy val `vnd.oipf.mippvcontrolmessage+xml`: MediaType =
      MediaType("application", "vnd.oipf.mippvcontrolmessage+xml", compressible = true, binary = true)

    lazy val `vnd.oipf.pae.gem`: MediaType =
      MediaType("application", "vnd.oipf.pae.gem", compressible = false, binary = true)

    lazy val `vnd.oipf.spdiscovery+xml`: MediaType =
      MediaType("application", "vnd.oipf.spdiscovery+xml", compressible = true, binary = true)

    lazy val `vnd.oipf.spdlist+xml`: MediaType =
      MediaType("application", "vnd.oipf.spdlist+xml", compressible = true, binary = true)

    lazy val `vnd.oipf.ueprofile+xml`: MediaType =
      MediaType("application", "vnd.oipf.ueprofile+xml", compressible = true, binary = true)

    lazy val `vnd.oipf.userprofile+xml`: MediaType =
      MediaType("application", "vnd.oipf.userprofile+xml", compressible = true, binary = true)

    lazy val `vnd.olpc-sugar`: MediaType =
      MediaType("application", "vnd.olpc-sugar", compressible = false, binary = true, fileExtensions = List("xo"))

    lazy val `vnd.oma-scws-config`: MediaType =
      MediaType("application", "vnd.oma-scws-config", compressible = false, binary = true)

    lazy val `vnd.oma-scws-http-request`: MediaType =
      MediaType("application", "vnd.oma-scws-http-request", compressible = false, binary = true)

    lazy val `vnd.oma-scws-http-response`: MediaType =
      MediaType("application", "vnd.oma-scws-http-response", compressible = false, binary = true)

    lazy val `vnd.oma.bcast.associated-procedure-parameter+xml`: MediaType =
      MediaType("application", "vnd.oma.bcast.associated-procedure-parameter+xml", compressible = true, binary = true)

    lazy val `vnd.oma.bcast.drm-trigger+xml`: MediaType =
      MediaType("application", "vnd.oma.bcast.drm-trigger+xml", compressible = true, binary = true)

    lazy val `vnd.oma.bcast.imd+xml`: MediaType =
      MediaType("application", "vnd.oma.bcast.imd+xml", compressible = true, binary = true)

    lazy val `vnd.oma.bcast.ltkm`: MediaType =
      MediaType("application", "vnd.oma.bcast.ltkm", compressible = false, binary = true)

    lazy val `vnd.oma.bcast.notification+xml`: MediaType =
      MediaType("application", "vnd.oma.bcast.notification+xml", compressible = true, binary = true)

    lazy val `vnd.oma.bcast.provisioningtrigger`: MediaType =
      MediaType("application", "vnd.oma.bcast.provisioningtrigger", compressible = false, binary = true)

    lazy val `vnd.oma.bcast.sgboot`: MediaType =
      MediaType("application", "vnd.oma.bcast.sgboot", compressible = false, binary = true)

    lazy val `vnd.oma.bcast.sgdd+xml`: MediaType =
      MediaType("application", "vnd.oma.bcast.sgdd+xml", compressible = true, binary = true)

    lazy val `vnd.oma.bcast.sgdu`: MediaType =
      MediaType("application", "vnd.oma.bcast.sgdu", compressible = false, binary = true)

    lazy val `vnd.oma.bcast.simple-symbol-container`: MediaType =
      MediaType("application", "vnd.oma.bcast.simple-symbol-container", compressible = false, binary = true)

    lazy val `vnd.oma.bcast.smartcard-trigger+xml`: MediaType =
      MediaType("application", "vnd.oma.bcast.smartcard-trigger+xml", compressible = true, binary = true)

    lazy val `vnd.oma.bcast.sprov+xml`: MediaType =
      MediaType("application", "vnd.oma.bcast.sprov+xml", compressible = true, binary = true)

    lazy val `vnd.oma.bcast.stkm`: MediaType =
      MediaType("application", "vnd.oma.bcast.stkm", compressible = false, binary = true)

    lazy val `vnd.oma.cab-address-book+xml`: MediaType =
      MediaType("application", "vnd.oma.cab-address-book+xml", compressible = true, binary = true)

    lazy val `vnd.oma.cab-feature-handler+xml`: MediaType =
      MediaType("application", "vnd.oma.cab-feature-handler+xml", compressible = true, binary = true)

    lazy val `vnd.oma.cab-pcc+xml`: MediaType =
      MediaType("application", "vnd.oma.cab-pcc+xml", compressible = true, binary = true)

    lazy val `vnd.oma.cab-subs-invite+xml`: MediaType =
      MediaType("application", "vnd.oma.cab-subs-invite+xml", compressible = true, binary = true)

    lazy val `vnd.oma.cab-user-prefs+xml`: MediaType =
      MediaType("application", "vnd.oma.cab-user-prefs+xml", compressible = true, binary = true)

    lazy val `vnd.oma.dcd`: MediaType =
      MediaType("application", "vnd.oma.dcd", compressible = false, binary = true)

    lazy val `vnd.oma.dcdc`: MediaType =
      MediaType("application", "vnd.oma.dcdc", compressible = false, binary = true)

    lazy val `vnd.oma.dd2+xml`: MediaType =
      MediaType("application", "vnd.oma.dd2+xml", compressible = true, binary = true, fileExtensions = List("dd2"))

    lazy val `vnd.oma.drm.risd+xml`: MediaType =
      MediaType("application", "vnd.oma.drm.risd+xml", compressible = true, binary = true)

    lazy val `vnd.oma.group-usage-list+xml`: MediaType =
      MediaType("application", "vnd.oma.group-usage-list+xml", compressible = true, binary = true)

    lazy val `vnd.oma.lwm2m+cbor`: MediaType =
      MediaType("application", "vnd.oma.lwm2m+cbor", compressible = false, binary = true)

    lazy val `vnd.oma.lwm2m+json`: MediaType =
      MediaType("application", "vnd.oma.lwm2m+json", compressible = true, binary = false)

    lazy val `vnd.oma.lwm2m+tlv`: MediaType =
      MediaType("application", "vnd.oma.lwm2m+tlv", compressible = false, binary = true)

    lazy val `vnd.oma.pal+xml`: MediaType =
      MediaType("application", "vnd.oma.pal+xml", compressible = true, binary = true)

    lazy val `vnd.oma.poc.detailed-progress-report+xml`: MediaType =
      MediaType("application", "vnd.oma.poc.detailed-progress-report+xml", compressible = true, binary = true)

    lazy val `vnd.oma.poc.final-report+xml`: MediaType =
      MediaType("application", "vnd.oma.poc.final-report+xml", compressible = true, binary = true)

    lazy val `vnd.oma.poc.groups+xml`: MediaType =
      MediaType("application", "vnd.oma.poc.groups+xml", compressible = true, binary = true)

    lazy val `vnd.oma.poc.invocation-descriptor+xml`: MediaType =
      MediaType("application", "vnd.oma.poc.invocation-descriptor+xml", compressible = true, binary = true)

    lazy val `vnd.oma.poc.optimized-progress-report+xml`: MediaType =
      MediaType("application", "vnd.oma.poc.optimized-progress-report+xml", compressible = true, binary = true)

    lazy val `vnd.oma.push`: MediaType =
      MediaType("application", "vnd.oma.push", compressible = false, binary = true)

    lazy val `vnd.oma.scidm.messages+xml`: MediaType =
      MediaType("application", "vnd.oma.scidm.messages+xml", compressible = true, binary = true)

    lazy val `vnd.oma.xcap-directory+xml`: MediaType =
      MediaType("application", "vnd.oma.xcap-directory+xml", compressible = true, binary = true)

    lazy val `vnd.omads-email+xml`: MediaType =
      MediaType("application", "vnd.omads-email+xml", compressible = true, binary = true)

    lazy val `vnd.omads-file+xml`: MediaType =
      MediaType("application", "vnd.omads-file+xml", compressible = true, binary = true)

    lazy val `vnd.omads-folder+xml`: MediaType =
      MediaType("application", "vnd.omads-folder+xml", compressible = true, binary = true)

    lazy val `vnd.omaloc-supl-init`: MediaType =
      MediaType("application", "vnd.omaloc-supl-init", compressible = false, binary = true)

    lazy val `vnd.oms.cellular-cose-content+cbor`: MediaType =
      MediaType("application", "vnd.oms.cellular-cose-content+cbor", compressible = false, binary = true)

    lazy val `vnd.onepager`: MediaType =
      MediaType("application", "vnd.onepager", compressible = false, binary = true)

    lazy val `vnd.onepagertamp`: MediaType =
      MediaType("application", "vnd.onepagertamp", compressible = false, binary = true)

    lazy val `vnd.onepagertamx`: MediaType =
      MediaType("application", "vnd.onepagertamx", compressible = false, binary = true)

    lazy val `vnd.onepagertat`: MediaType =
      MediaType("application", "vnd.onepagertat", compressible = false, binary = true)

    lazy val `vnd.onepagertatp`: MediaType =
      MediaType("application", "vnd.onepagertatp", compressible = false, binary = true)

    lazy val `vnd.onepagertatx`: MediaType =
      MediaType("application", "vnd.onepagertatx", compressible = false, binary = true)

    lazy val `vnd.onvif.metadata`: MediaType =
      MediaType("application", "vnd.onvif.metadata", compressible = false, binary = true)

    lazy val `vnd.openblox.game+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openblox.game+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("obgx")
      )

    lazy val `vnd.openblox.game-binary`: MediaType =
      MediaType("application", "vnd.openblox.game-binary", compressible = false, binary = true)

    lazy val `vnd.openeye.oeb`: MediaType =
      MediaType("application", "vnd.openeye.oeb", compressible = false, binary = true)

    lazy val `vnd.openofficeorg.extension`: MediaType =
      MediaType(
        "application",
        "vnd.openofficeorg.extension",
        compressible = false,
        binary = true,
        fileExtensions = List("oxt")
      )

    lazy val `vnd.openprinttag`: MediaType =
      MediaType("application", "vnd.openprinttag", compressible = false, binary = true)

    lazy val `vnd.openstreetmap.data+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openstreetmap.data+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("osm")
      )

    lazy val `vnd.opentimestamps.ots`: MediaType =
      MediaType("application", "vnd.opentimestamps.ots", compressible = false, binary = true)

    lazy val `vnd.openvpi.dspx+json`: MediaType =
      MediaType("application", "vnd.openvpi.dspx+json", compressible = true, binary = false)

    lazy val `vnd.openxmlformats-officedocument.custom-properties+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.custom-properties+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.customxmlproperties+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.customxmlproperties+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.drawing+xml`: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.drawing+xml", compressible = true, binary = true)

    lazy val `vnd.openxmlformats-officedocument.drawingml.chart+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.chart+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.drawingml.chartshapes+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.chartshapes+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.drawingml.diagramdata+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.diagramdata+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.extended-properties+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.extended-properties+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.commentauthors+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.commentauthors+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.comments+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.comments+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.notesmaster+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.notesmaster+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.notesslide+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.notesslide+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.presentation`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.presentation",
        compressible = false,
        binary = true,
        fileExtensions = List("pptx")
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.presentation.main+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.presprops+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.presprops+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.slide`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slide",
        compressible = false,
        binary = true,
        fileExtensions = List("sldx")
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.slide+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slide+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.slidelayout+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slidelayout+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.slidemaster+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slidemaster+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.slideshow`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slideshow",
        compressible = false,
        binary = true,
        fileExtensions = List("ppsx")
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.tablestyles+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.tablestyles+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.tags+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.tags+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.template`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.template",
        compressible = false,
        binary = true,
        fileExtensions = List("potx")
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.template.main+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.template.main+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.presentationml.viewprops+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.presentationml.viewprops+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.comments+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.comments+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.connections+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.connections+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.sheet`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        compressible = false,
        binary = true,
        fileExtensions = List("xlsx")
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.styles+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.styles+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.table+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.table+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.template`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.template",
        compressible = false,
        binary = true,
        fileExtensions = List("xltx")
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.theme+xml`: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.theme+xml", compressible = true, binary = true)

    lazy val `vnd.openxmlformats-officedocument.themeoverride+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.themeoverride+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.vmldrawing`: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.vmldrawing", compressible = false, binary = true)

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.comments+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.comments+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.document`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.document",
        compressible = false,
        binary = true,
        fileExtensions = List("docx")
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.footer+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.footer+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.settings+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.settings+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.styles+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.styles+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.template`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.template",
        compressible = false,
        binary = true,
        fileExtensions = List("dotx")
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-package.core-properties+xml`: MediaType =
      MediaType("application", "vnd.openxmlformats-package.core-properties+xml", compressible = true, binary = true)

    lazy val `vnd.openxmlformats-package.digital-signature-xmlsignature+xml`: MediaType =
      MediaType(
        "application",
        "vnd.openxmlformats-package.digital-signature-xmlsignature+xml",
        compressible = true,
        binary = true
      )

    lazy val `vnd.openxmlformats-package.relationships+xml`: MediaType =
      MediaType("application", "vnd.openxmlformats-package.relationships+xml", compressible = true, binary = true)

    lazy val `vnd.oracle.resource+json`: MediaType =
      MediaType("application", "vnd.oracle.resource+json", compressible = true, binary = false)

    lazy val `vnd.orange.indata`: MediaType =
      MediaType("application", "vnd.orange.indata", compressible = false, binary = true)

    lazy val `vnd.osa.netdeploy`: MediaType =
      MediaType("application", "vnd.osa.netdeploy", compressible = false, binary = true)

    lazy val `vnd.osgeo.mapguide.package`: MediaType =
      MediaType(
        "application",
        "vnd.osgeo.mapguide.package",
        compressible = false,
        binary = true,
        fileExtensions = List("mgp")
      )

    lazy val `vnd.osgi.bundle`: MediaType =
      MediaType("application", "vnd.osgi.bundle", compressible = false, binary = true)

    lazy val `vnd.osgi.dp`: MediaType =
      MediaType("application", "vnd.osgi.dp", compressible = false, binary = true, fileExtensions = List("dp"))

    lazy val `vnd.osgi.subsystem`: MediaType =
      MediaType("application", "vnd.osgi.subsystem", compressible = false, binary = true, fileExtensions = List("esa"))

    lazy val `vnd.otps.ct-kip+xml`: MediaType =
      MediaType("application", "vnd.otps.ct-kip+xml", compressible = true, binary = true)

    lazy val `vnd.oxli.countgraph`: MediaType =
      MediaType("application", "vnd.oxli.countgraph", compressible = false, binary = true)

    lazy val `vnd.pagerduty+json`: MediaType =
      MediaType("application", "vnd.pagerduty+json", compressible = true, binary = false)

    lazy val `vnd.palm`: MediaType =
      MediaType(
        "application",
        "vnd.palm",
        compressible = false,
        binary = true,
        fileExtensions = List("pdb", "pqa", "oprc")
      )

    lazy val `vnd.panoply`: MediaType =
      MediaType("application", "vnd.panoply", compressible = false, binary = true)

    lazy val `vnd.paos.xml`: MediaType =
      MediaType("application", "vnd.paos.xml", compressible = false, binary = true)

    lazy val `vnd.patentdive`: MediaType =
      MediaType("application", "vnd.patentdive", compressible = false, binary = true)

    lazy val `vnd.patientecommsdoc`: MediaType =
      MediaType("application", "vnd.patientecommsdoc", compressible = false, binary = true)

    lazy val `vnd.pawaafile`: MediaType =
      MediaType("application", "vnd.pawaafile", compressible = false, binary = true, fileExtensions = List("paw"))

    lazy val `vnd.pcos`: MediaType =
      MediaType("application", "vnd.pcos", compressible = false, binary = true)

    lazy val `vnd.pg.format`: MediaType =
      MediaType("application", "vnd.pg.format", compressible = false, binary = true, fileExtensions = List("str"))

    lazy val `vnd.pg.osasli`: MediaType =
      MediaType("application", "vnd.pg.osasli", compressible = false, binary = true, fileExtensions = List("ei6"))

    lazy val `vnd.piaccess.application-licence`: MediaType =
      MediaType("application", "vnd.piaccess.application-licence", compressible = false, binary = true)

    lazy val `vnd.picsel`: MediaType =
      MediaType("application", "vnd.picsel", compressible = false, binary = true, fileExtensions = List("efif"))

    lazy val `vnd.pmi.widget`: MediaType =
      MediaType("application", "vnd.pmi.widget", compressible = false, binary = true, fileExtensions = List("wg"))

    lazy val `vnd.pmtiles`: MediaType =
      MediaType("application", "vnd.pmtiles", compressible = false, binary = true)

    lazy val `vnd.poc.group-advertisement+xml`: MediaType =
      MediaType("application", "vnd.poc.group-advertisement+xml", compressible = true, binary = true)

    lazy val `vnd.pocketlearn`: MediaType =
      MediaType("application", "vnd.pocketlearn", compressible = false, binary = true, fileExtensions = List("plf"))

    lazy val `vnd.powerbuilder6`: MediaType =
      MediaType("application", "vnd.powerbuilder6", compressible = false, binary = true, fileExtensions = List("pbd"))

    lazy val `vnd.powerbuilder6-s`: MediaType =
      MediaType("application", "vnd.powerbuilder6-s", compressible = false, binary = true)

    lazy val `vnd.powerbuilder7`: MediaType =
      MediaType("application", "vnd.powerbuilder7", compressible = false, binary = true)

    lazy val `vnd.powerbuilder7-s`: MediaType =
      MediaType("application", "vnd.powerbuilder7-s", compressible = false, binary = true)

    lazy val `vnd.powerbuilder75`: MediaType =
      MediaType("application", "vnd.powerbuilder75", compressible = false, binary = true)

    lazy val `vnd.powerbuilder75-s`: MediaType =
      MediaType("application", "vnd.powerbuilder75-s", compressible = false, binary = true)

    lazy val `vnd.pp.systemverify+xml`: MediaType =
      MediaType(
        "application",
        "vnd.pp.systemverify+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("systemverify")
      )

    lazy val `vnd.preminet`: MediaType =
      MediaType("application", "vnd.preminet", compressible = false, binary = true)

    lazy val `vnd.previewsystems.box`: MediaType =
      MediaType(
        "application",
        "vnd.previewsystems.box",
        compressible = false,
        binary = true,
        fileExtensions = List("box")
      )

    lazy val `vnd.procreate.brush`: MediaType =
      MediaType(
        "application",
        "vnd.procreate.brush",
        compressible = false,
        binary = true,
        fileExtensions = List("brush")
      )

    lazy val `vnd.procreate.brushset`: MediaType =
      MediaType(
        "application",
        "vnd.procreate.brushset",
        compressible = false,
        binary = true,
        fileExtensions = List("brushset")
      )

    lazy val `vnd.procreate.dream`: MediaType =
      MediaType("application", "vnd.procreate.dream", compressible = false, binary = true, fileExtensions = List("drm"))

    lazy val `vnd.project-graph`: MediaType =
      MediaType("application", "vnd.project-graph", compressible = false, binary = true)

    lazy val `vnd.proteus.magazine`: MediaType =
      MediaType(
        "application",
        "vnd.proteus.magazine",
        compressible = false,
        binary = true,
        fileExtensions = List("mgz")
      )

    lazy val `vnd.psfs`: MediaType =
      MediaType("application", "vnd.psfs", compressible = false, binary = true)

    lazy val `vnd.pt.mundusmundi`: MediaType =
      MediaType("application", "vnd.pt.mundusmundi", compressible = false, binary = true)

    lazy val `vnd.publishare-delta-tree`: MediaType =
      MediaType(
        "application",
        "vnd.publishare-delta-tree",
        compressible = false,
        binary = true,
        fileExtensions = List("qps")
      )

    lazy val `vnd.pvi.ptid1`: MediaType =
      MediaType("application", "vnd.pvi.ptid1", compressible = false, binary = true, fileExtensions = List("ptid"))

    lazy val `vnd.pwg-multiplexed`: MediaType =
      MediaType("application", "vnd.pwg-multiplexed", compressible = false, binary = true)

    lazy val `vnd.pwg-xhtml-print+xml`: MediaType =
      MediaType(
        "application",
        "vnd.pwg-xhtml-print+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("xhtm")
      )

    lazy val `vnd.pyon+json`: MediaType =
      MediaType("application", "vnd.pyon+json", compressible = true, binary = false)

    lazy val `vnd.qualcomm.brew-app-res`: MediaType =
      MediaType("application", "vnd.qualcomm.brew-app-res", compressible = false, binary = true)

    lazy val `vnd.quarantainenet`: MediaType =
      MediaType("application", "vnd.quarantainenet", compressible = false, binary = true)

    lazy val `vnd.quark.quarkxpress`: MediaType =
      MediaType(
        "application",
        "vnd.quark.quarkxpress",
        compressible = false,
        binary = true,
        fileExtensions = List("qxd", "qxt", "qwd", "qwt", "qxl", "qxb")
      )

    lazy val `vnd.quobject-quoxdocument`: MediaType =
      MediaType("application", "vnd.quobject-quoxdocument", compressible = false, binary = true)

    lazy val `vnd.r74n.sandboxels+json`: MediaType =
      MediaType("application", "vnd.r74n.sandboxels+json", compressible = true, binary = false)

    lazy val `vnd.radisys.moml+xml`: MediaType =
      MediaType("application", "vnd.radisys.moml+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-audit+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-audit+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-audit-conf+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-conf+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-audit-conn+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-conn+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-audit-dialog+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-dialog+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-audit-stream+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-stream+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-conf+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-conf+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-dialog+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-dialog-base+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-base+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-dialog-fax-detect+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-fax-detect+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-dialog-fax-sendrecv+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-fax-sendrecv+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-dialog-group+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-group+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-dialog-speech+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-speech+xml", compressible = true, binary = true)

    lazy val `vnd.radisys.msml-dialog-transform+xml`: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-transform+xml", compressible = true, binary = true)

    lazy val `vnd.rainstor.data`: MediaType =
      MediaType("application", "vnd.rainstor.data", compressible = false, binary = true)

    lazy val `vnd.rapid`: MediaType =
      MediaType("application", "vnd.rapid", compressible = false, binary = true)

    lazy val `vnd.rar`: MediaType =
      MediaType("application", "vnd.rar", compressible = false, binary = true, fileExtensions = List("rar"))

    lazy val `vnd.realvnc.bed`: MediaType =
      MediaType("application", "vnd.realvnc.bed", compressible = false, binary = true, fileExtensions = List("bed"))

    lazy val `vnd.recordare.musicxml`: MediaType =
      MediaType(
        "application",
        "vnd.recordare.musicxml",
        compressible = false,
        binary = true,
        fileExtensions = List("mxl")
      )

    lazy val `vnd.recordare.musicxml+xml`: MediaType =
      MediaType(
        "application",
        "vnd.recordare.musicxml+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("musicxml")
      )

    lazy val `vnd.relpipe`: MediaType =
      MediaType("application", "vnd.relpipe", compressible = false, binary = true)

    lazy val `vnd.renlearn.rlprint`: MediaType =
      MediaType("application", "vnd.renlearn.rlprint", compressible = false, binary = true)

    lazy val `vnd.resilient.logic`: MediaType =
      MediaType("application", "vnd.resilient.logic", compressible = false, binary = true)

    lazy val `vnd.restful+json`: MediaType =
      MediaType("application", "vnd.restful+json", compressible = true, binary = false)

    lazy val `vnd.rig.cryptonote`: MediaType =
      MediaType(
        "application",
        "vnd.rig.cryptonote",
        compressible = false,
        binary = true,
        fileExtensions = List("cryptonote")
      )

    lazy val `vnd.rim.cod`: MediaType =
      MediaType("application", "vnd.rim.cod", compressible = false, binary = true, fileExtensions = List("cod"))

    lazy val `vnd.rn-realmedia`: MediaType =
      MediaType("application", "vnd.rn-realmedia", compressible = false, binary = true, fileExtensions = List("rm"))

    lazy val `vnd.rn-realmedia-vbr`: MediaType =
      MediaType(
        "application",
        "vnd.rn-realmedia-vbr",
        compressible = false,
        binary = true,
        fileExtensions = List("rmvb")
      )

    lazy val `vnd.route66.link66+xml`: MediaType =
      MediaType(
        "application",
        "vnd.route66.link66+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("link66")
      )

    lazy val `vnd.rs-274x`: MediaType =
      MediaType("application", "vnd.rs-274x", compressible = false, binary = true)

    lazy val `vnd.ruckus.download`: MediaType =
      MediaType("application", "vnd.ruckus.download", compressible = false, binary = true)

    lazy val `vnd.s3sms`: MediaType =
      MediaType("application", "vnd.s3sms", compressible = false, binary = true)

    lazy val `vnd.sailingtracker.track`: MediaType =
      MediaType(
        "application",
        "vnd.sailingtracker.track",
        compressible = false,
        binary = true,
        fileExtensions = List("st")
      )

    lazy val `vnd.sar`: MediaType =
      MediaType("application", "vnd.sar", compressible = false, binary = true)

    lazy val `vnd.sbm.cid`: MediaType =
      MediaType("application", "vnd.sbm.cid", compressible = false, binary = true)

    lazy val `vnd.sbm.mid2`: MediaType =
      MediaType("application", "vnd.sbm.mid2", compressible = false, binary = true)

    lazy val `vnd.scribus`: MediaType =
      MediaType("application", "vnd.scribus", compressible = false, binary = true)

    lazy val `vnd.sealed.3df`: MediaType =
      MediaType("application", "vnd.sealed.3df", compressible = false, binary = true)

    lazy val `vnd.sealed.csf`: MediaType =
      MediaType("application", "vnd.sealed.csf", compressible = false, binary = true)

    lazy val `vnd.sealed.doc`: MediaType =
      MediaType("application", "vnd.sealed.doc", compressible = false, binary = true)

    lazy val `vnd.sealed.eml`: MediaType =
      MediaType("application", "vnd.sealed.eml", compressible = false, binary = true)

    lazy val `vnd.sealed.mht`: MediaType =
      MediaType("application", "vnd.sealed.mht", compressible = false, binary = true)

    lazy val `vnd.sealed.net`: MediaType =
      MediaType("application", "vnd.sealed.net", compressible = false, binary = true)

    lazy val `vnd.sealed.ppt`: MediaType =
      MediaType("application", "vnd.sealed.ppt", compressible = false, binary = true)

    lazy val `vnd.sealed.tiff`: MediaType =
      MediaType("application", "vnd.sealed.tiff", compressible = false, binary = true)

    lazy val `vnd.sealed.xls`: MediaType =
      MediaType("application", "vnd.sealed.xls", compressible = false, binary = true)

    lazy val `vnd.sealedmedia.softseal.html`: MediaType =
      MediaType("application", "vnd.sealedmedia.softseal.html", compressible = false, binary = true)

    lazy val `vnd.sealedmedia.softseal.pdf`: MediaType =
      MediaType("application", "vnd.sealedmedia.softseal.pdf", compressible = false, binary = true)

    lazy val `vnd.seemail`: MediaType =
      MediaType("application", "vnd.seemail", compressible = false, binary = true, fileExtensions = List("see"))

    lazy val `vnd.seis+json`: MediaType =
      MediaType("application", "vnd.seis+json", compressible = true, binary = false)

    lazy val `vnd.sema`: MediaType =
      MediaType("application", "vnd.sema", compressible = false, binary = true, fileExtensions = List("sema"))

    lazy val `vnd.semd`: MediaType =
      MediaType("application", "vnd.semd", compressible = false, binary = true, fileExtensions = List("semd"))

    lazy val `vnd.semf`: MediaType =
      MediaType("application", "vnd.semf", compressible = false, binary = true, fileExtensions = List("semf"))

    lazy val `vnd.shade-save-file`: MediaType =
      MediaType("application", "vnd.shade-save-file", compressible = false, binary = true)

    lazy val `vnd.shana.informed.formdata`: MediaType =
      MediaType(
        "application",
        "vnd.shana.informed.formdata",
        compressible = false,
        binary = true,
        fileExtensions = List("ifm")
      )

    lazy val `vnd.shana.informed.formtemplate`: MediaType =
      MediaType(
        "application",
        "vnd.shana.informed.formtemplate",
        compressible = false,
        binary = true,
        fileExtensions = List("itp")
      )

    lazy val `vnd.shana.informed.interchange`: MediaType =
      MediaType(
        "application",
        "vnd.shana.informed.interchange",
        compressible = false,
        binary = true,
        fileExtensions = List("iif")
      )

    lazy val `vnd.shana.informed.package`: MediaType =
      MediaType(
        "application",
        "vnd.shana.informed.package",
        compressible = false,
        binary = true,
        fileExtensions = List("ipk")
      )

    lazy val `vnd.shootproof+json`: MediaType =
      MediaType("application", "vnd.shootproof+json", compressible = true, binary = false)

    lazy val `vnd.shopkick+json`: MediaType =
      MediaType("application", "vnd.shopkick+json", compressible = true, binary = false)

    lazy val `vnd.shp`: MediaType =
      MediaType("application", "vnd.shp", compressible = false, binary = true)

    lazy val `vnd.shx`: MediaType =
      MediaType("application", "vnd.shx", compressible = false, binary = true)

    lazy val `vnd.sigrok.session`: MediaType =
      MediaType("application", "vnd.sigrok.session", compressible = false, binary = true)

    lazy val `vnd.simtech-mindmapper`: MediaType =
      MediaType(
        "application",
        "vnd.simtech-mindmapper",
        compressible = false,
        binary = true,
        fileExtensions = List("twd", "twds")
      )

    lazy val `vnd.siren+json`: MediaType =
      MediaType("application", "vnd.siren+json", compressible = true, binary = false)

    lazy val `vnd.sirtx.vmv0`: MediaType =
      MediaType("application", "vnd.sirtx.vmv0", compressible = false, binary = true)

    lazy val `vnd.sketchometry`: MediaType =
      MediaType("application", "vnd.sketchometry", compressible = false, binary = true)

    lazy val `vnd.smaf`: MediaType =
      MediaType("application", "vnd.smaf", compressible = false, binary = true, fileExtensions = List("mmf"))

    lazy val `vnd.smart.notebook`: MediaType =
      MediaType("application", "vnd.smart.notebook", compressible = false, binary = true)

    lazy val `vnd.smart.teacher`: MediaType =
      MediaType(
        "application",
        "vnd.smart.teacher",
        compressible = false,
        binary = true,
        fileExtensions = List("teacher")
      )

    lazy val `vnd.smintio.portals.archive`: MediaType =
      MediaType("application", "vnd.smintio.portals.archive", compressible = false, binary = true)

    lazy val `vnd.snesdev-page-table`: MediaType =
      MediaType("application", "vnd.snesdev-page-table", compressible = false, binary = true)

    lazy val `vnd.software602.filler.form+xml`: MediaType =
      MediaType(
        "application",
        "vnd.software602.filler.form+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("fo")
      )

    lazy val `vnd.software602.filler.form-xml-zip`: MediaType =
      MediaType("application", "vnd.software602.filler.form-xml-zip", compressible = false, binary = true)

    lazy val `vnd.solent.sdkm+xml`: MediaType =
      MediaType(
        "application",
        "vnd.solent.sdkm+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("sdkm", "sdkd")
      )

    lazy val `vnd.spotfire.dxp`: MediaType =
      MediaType("application", "vnd.spotfire.dxp", compressible = false, binary = true, fileExtensions = List("dxp"))

    lazy val `vnd.spotfire.sfs`: MediaType =
      MediaType("application", "vnd.spotfire.sfs", compressible = false, binary = true, fileExtensions = List("sfs"))

    lazy val `vnd.sqlite3`: MediaType =
      MediaType(
        "application",
        "vnd.sqlite3",
        compressible = false,
        binary = true,
        fileExtensions = List("sqlite", "sqlite3")
      )

    lazy val `vnd.sss-cod`: MediaType =
      MediaType("application", "vnd.sss-cod", compressible = false, binary = true)

    lazy val `vnd.sss-dtf`: MediaType =
      MediaType("application", "vnd.sss-dtf", compressible = false, binary = true)

    lazy val `vnd.sss-ntf`: MediaType =
      MediaType("application", "vnd.sss-ntf", compressible = false, binary = true)

    lazy val `vnd.stardivision.calc`: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.calc",
        compressible = false,
        binary = true,
        fileExtensions = List("sdc")
      )

    lazy val `vnd.stardivision.draw`: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.draw",
        compressible = false,
        binary = true,
        fileExtensions = List("sda")
      )

    lazy val `vnd.stardivision.impress`: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.impress",
        compressible = false,
        binary = true,
        fileExtensions = List("sdd")
      )

    lazy val `vnd.stardivision.math`: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.math",
        compressible = false,
        binary = true,
        fileExtensions = List("smf")
      )

    lazy val `vnd.stardivision.writer`: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.writer",
        compressible = false,
        binary = true,
        fileExtensions = List("sdw", "vor")
      )

    lazy val `vnd.stardivision.writer-global`: MediaType =
      MediaType(
        "application",
        "vnd.stardivision.writer-global",
        compressible = false,
        binary = true,
        fileExtensions = List("sgl")
      )

    lazy val `vnd.stepmania.package`: MediaType =
      MediaType(
        "application",
        "vnd.stepmania.package",
        compressible = false,
        binary = true,
        fileExtensions = List("smzip")
      )

    lazy val `vnd.stepmania.stepchart`: MediaType =
      MediaType(
        "application",
        "vnd.stepmania.stepchart",
        compressible = false,
        binary = true,
        fileExtensions = List("sm")
      )

    lazy val `vnd.street-stream`: MediaType =
      MediaType("application", "vnd.street-stream", compressible = false, binary = true)

    lazy val `vnd.sun.wadl+xml`: MediaType =
      MediaType("application", "vnd.sun.wadl+xml", compressible = true, binary = true, fileExtensions = List("wadl"))

    lazy val `vnd.sun.xml.calc`: MediaType =
      MediaType("application", "vnd.sun.xml.calc", compressible = false, binary = true, fileExtensions = List("sxc"))

    lazy val `vnd.sun.xml.calc.template`: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.calc.template",
        compressible = false,
        binary = true,
        fileExtensions = List("stc")
      )

    lazy val `vnd.sun.xml.draw`: MediaType =
      MediaType("application", "vnd.sun.xml.draw", compressible = false, binary = true, fileExtensions = List("sxd"))

    lazy val `vnd.sun.xml.draw.template`: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.draw.template",
        compressible = false,
        binary = true,
        fileExtensions = List("std")
      )

    lazy val `vnd.sun.xml.impress`: MediaType =
      MediaType("application", "vnd.sun.xml.impress", compressible = false, binary = true, fileExtensions = List("sxi"))

    lazy val `vnd.sun.xml.impress.template`: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.impress.template",
        compressible = false,
        binary = true,
        fileExtensions = List("sti")
      )

    lazy val `vnd.sun.xml.math`: MediaType =
      MediaType("application", "vnd.sun.xml.math", compressible = false, binary = true, fileExtensions = List("sxm"))

    lazy val `vnd.sun.xml.writer`: MediaType =
      MediaType("application", "vnd.sun.xml.writer", compressible = false, binary = true, fileExtensions = List("sxw"))

    lazy val `vnd.sun.xml.writer.global`: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.writer.global",
        compressible = false,
        binary = true,
        fileExtensions = List("sxg")
      )

    lazy val `vnd.sun.xml.writer.template`: MediaType =
      MediaType(
        "application",
        "vnd.sun.xml.writer.template",
        compressible = false,
        binary = true,
        fileExtensions = List("stw")
      )

    lazy val `vnd.superfile.super`: MediaType =
      MediaType("application", "vnd.superfile.super", compressible = false, binary = true)

    lazy val `vnd.sus-calendar`: MediaType =
      MediaType(
        "application",
        "vnd.sus-calendar",
        compressible = false,
        binary = true,
        fileExtensions = List("sus", "susp")
      )

    lazy val `vnd.svd`: MediaType =
      MediaType("application", "vnd.svd", compressible = false, binary = true, fileExtensions = List("svd"))

    lazy val `vnd.swiftview-ics`: MediaType =
      MediaType("application", "vnd.swiftview-ics", compressible = false, binary = true)

    lazy val `vnd.sybyl.mol2`: MediaType =
      MediaType("application", "vnd.sybyl.mol2", compressible = false, binary = true)

    lazy val `vnd.sycle+xml`: MediaType =
      MediaType("application", "vnd.sycle+xml", compressible = true, binary = true)

    lazy val `vnd.syft+json`: MediaType =
      MediaType("application", "vnd.syft+json", compressible = true, binary = false)

    lazy val `vnd.symbian.install`: MediaType =
      MediaType(
        "application",
        "vnd.symbian.install",
        compressible = false,
        binary = true,
        fileExtensions = List("sis", "sisx")
      )

    lazy val `vnd.syncml+xml`: MediaType =
      MediaType("application", "vnd.syncml+xml", compressible = true, binary = true, fileExtensions = List("xsm"))

    lazy val `vnd.syncml.dm+wbxml`: MediaType =
      MediaType("application", "vnd.syncml.dm+wbxml", compressible = false, binary = true, fileExtensions = List("bdm"))

    lazy val `vnd.syncml.dm+xml`: MediaType =
      MediaType("application", "vnd.syncml.dm+xml", compressible = true, binary = true, fileExtensions = List("xdm"))

    lazy val `vnd.syncml.dm.notification`: MediaType =
      MediaType("application", "vnd.syncml.dm.notification", compressible = false, binary = true)

    lazy val `vnd.syncml.dmddf+wbxml`: MediaType =
      MediaType("application", "vnd.syncml.dmddf+wbxml", compressible = false, binary = true)

    lazy val `vnd.syncml.dmddf+xml`: MediaType =
      MediaType("application", "vnd.syncml.dmddf+xml", compressible = true, binary = true, fileExtensions = List("ddf"))

    lazy val `vnd.syncml.dmtnds+wbxml`: MediaType =
      MediaType("application", "vnd.syncml.dmtnds+wbxml", compressible = false, binary = true)

    lazy val `vnd.syncml.dmtnds+xml`: MediaType =
      MediaType("application", "vnd.syncml.dmtnds+xml", compressible = true, binary = true)

    lazy val `vnd.syncml.ds.notification`: MediaType =
      MediaType("application", "vnd.syncml.ds.notification", compressible = false, binary = true)

    lazy val `vnd.tableschema+json`: MediaType =
      MediaType("application", "vnd.tableschema+json", compressible = true, binary = false)

    lazy val `vnd.tao.intent-module-archive`: MediaType =
      MediaType(
        "application",
        "vnd.tao.intent-module-archive",
        compressible = false,
        binary = true,
        fileExtensions = List("tao")
      )

    lazy val `vnd.tcpdump.pcap`: MediaType =
      MediaType(
        "application",
        "vnd.tcpdump.pcap",
        compressible = false,
        binary = true,
        fileExtensions = List("pcap", "cap", "dmp")
      )

    lazy val `vnd.think-cell.ppttc+json`: MediaType =
      MediaType("application", "vnd.think-cell.ppttc+json", compressible = true, binary = false)

    lazy val `vnd.tmd.mediaflex.api+xml`: MediaType =
      MediaType("application", "vnd.tmd.mediaflex.api+xml", compressible = true, binary = true)

    lazy val `vnd.tml`: MediaType =
      MediaType("application", "vnd.tml", compressible = false, binary = true)

    lazy val `vnd.tmobile-livetv`: MediaType =
      MediaType("application", "vnd.tmobile-livetv", compressible = false, binary = true, fileExtensions = List("tmo"))

    lazy val `vnd.tri.onesource`: MediaType =
      MediaType("application", "vnd.tri.onesource", compressible = false, binary = true)

    lazy val `vnd.trid.tpt`: MediaType =
      MediaType("application", "vnd.trid.tpt", compressible = false, binary = true, fileExtensions = List("tpt"))

    lazy val `vnd.triscape.mxs`: MediaType =
      MediaType("application", "vnd.triscape.mxs", compressible = false, binary = true, fileExtensions = List("mxs"))

    lazy val `vnd.trueapp`: MediaType =
      MediaType("application", "vnd.trueapp", compressible = false, binary = true, fileExtensions = List("tra"))

    lazy val `vnd.truedoc`: MediaType =
      MediaType("application", "vnd.truedoc", compressible = false, binary = true)

    lazy val `vnd.ubisoft.webplayer`: MediaType =
      MediaType("application", "vnd.ubisoft.webplayer", compressible = false, binary = true)

    lazy val `vnd.ufdl`: MediaType =
      MediaType("application", "vnd.ufdl", compressible = false, binary = true, fileExtensions = List("ufd", "ufdl"))

    lazy val `vnd.uic.dosipas.v1`: MediaType =
      MediaType("application", "vnd.uic.dosipas.v1", compressible = false, binary = true)

    lazy val `vnd.uic.dosipas.v2`: MediaType =
      MediaType("application", "vnd.uic.dosipas.v2", compressible = false, binary = true)

    lazy val `vnd.uic.osdm+json`: MediaType =
      MediaType("application", "vnd.uic.osdm+json", compressible = true, binary = false)

    lazy val `vnd.uic.tlb-fcb`: MediaType =
      MediaType("application", "vnd.uic.tlb-fcb", compressible = false, binary = true)

    lazy val `vnd.uiq.theme`: MediaType =
      MediaType("application", "vnd.uiq.theme", compressible = false, binary = true, fileExtensions = List("utz"))

    lazy val `vnd.umajin`: MediaType =
      MediaType("application", "vnd.umajin", compressible = false, binary = true, fileExtensions = List("umj"))

    lazy val `vnd.unity`: MediaType =
      MediaType("application", "vnd.unity", compressible = false, binary = true, fileExtensions = List("unityweb"))

    lazy val `vnd.uoml+xml`: MediaType =
      MediaType("application", "vnd.uoml+xml", compressible = true, binary = true, fileExtensions = List("uoml", "uo"))

    lazy val `vnd.uplanet.alert`: MediaType =
      MediaType("application", "vnd.uplanet.alert", compressible = false, binary = true)

    lazy val `vnd.uplanet.alert-wbxml`: MediaType =
      MediaType("application", "vnd.uplanet.alert-wbxml", compressible = false, binary = true)

    lazy val `vnd.uplanet.bearer-choice`: MediaType =
      MediaType("application", "vnd.uplanet.bearer-choice", compressible = false, binary = true)

    lazy val `vnd.uplanet.bearer-choice-wbxml`: MediaType =
      MediaType("application", "vnd.uplanet.bearer-choice-wbxml", compressible = false, binary = true)

    lazy val `vnd.uplanet.cacheop`: MediaType =
      MediaType("application", "vnd.uplanet.cacheop", compressible = false, binary = true)

    lazy val `vnd.uplanet.cacheop-wbxml`: MediaType =
      MediaType("application", "vnd.uplanet.cacheop-wbxml", compressible = false, binary = true)

    lazy val `vnd.uplanet.channel`: MediaType =
      MediaType("application", "vnd.uplanet.channel", compressible = false, binary = true)

    lazy val `vnd.uplanet.channel-wbxml`: MediaType =
      MediaType("application", "vnd.uplanet.channel-wbxml", compressible = false, binary = true)

    lazy val `vnd.uplanet.list`: MediaType =
      MediaType("application", "vnd.uplanet.list", compressible = false, binary = true)

    lazy val `vnd.uplanet.list-wbxml`: MediaType =
      MediaType("application", "vnd.uplanet.list-wbxml", compressible = false, binary = true)

    lazy val `vnd.uplanet.listcmd`: MediaType =
      MediaType("application", "vnd.uplanet.listcmd", compressible = false, binary = true)

    lazy val `vnd.uplanet.listcmd-wbxml`: MediaType =
      MediaType("application", "vnd.uplanet.listcmd-wbxml", compressible = false, binary = true)

    lazy val `vnd.uplanet.signal`: MediaType =
      MediaType("application", "vnd.uplanet.signal", compressible = false, binary = true)

    lazy val `vnd.uri-map`: MediaType =
      MediaType("application", "vnd.uri-map", compressible = false, binary = true)

    lazy val `vnd.valve.source.material`: MediaType =
      MediaType("application", "vnd.valve.source.material", compressible = false, binary = true)

    lazy val `vnd.vcx`: MediaType =
      MediaType("application", "vnd.vcx", compressible = false, binary = true, fileExtensions = List("vcx"))

    lazy val `vnd.vd-study`: MediaType =
      MediaType("application", "vnd.vd-study", compressible = false, binary = true)

    lazy val `vnd.vectorworks`: MediaType =
      MediaType("application", "vnd.vectorworks", compressible = false, binary = true)

    lazy val `vnd.vel+json`: MediaType =
      MediaType("application", "vnd.vel+json", compressible = true, binary = false)

    lazy val `vnd.veraison.tsm-report+cbor`: MediaType =
      MediaType("application", "vnd.veraison.tsm-report+cbor", compressible = false, binary = true)

    lazy val `vnd.veraison.tsm-report+json`: MediaType =
      MediaType("application", "vnd.veraison.tsm-report+json", compressible = true, binary = false)

    lazy val `vnd.verifier-attestation+jwt`: MediaType =
      MediaType("application", "vnd.verifier-attestation+jwt", compressible = false, binary = true)

    lazy val `vnd.verimatrix.vcas`: MediaType =
      MediaType("application", "vnd.verimatrix.vcas", compressible = false, binary = true)

    lazy val `vnd.veritone.aion+json`: MediaType =
      MediaType("application", "vnd.veritone.aion+json", compressible = true, binary = false)

    lazy val `vnd.veryant.thin`: MediaType =
      MediaType("application", "vnd.veryant.thin", compressible = false, binary = true)

    lazy val `vnd.ves.encrypted`: MediaType =
      MediaType("application", "vnd.ves.encrypted", compressible = false, binary = true)

    lazy val `vnd.vidsoft.vidconference`: MediaType =
      MediaType("application", "vnd.vidsoft.vidconference", compressible = false, binary = true)

    lazy val `vnd.visio`: MediaType =
      MediaType(
        "application",
        "vnd.visio",
        compressible = false,
        binary = true,
        fileExtensions = List("vsd", "vst", "vss", "vsw", "vsdx", "vtx")
      )

    lazy val `vnd.visionary`: MediaType =
      MediaType("application", "vnd.visionary", compressible = false, binary = true, fileExtensions = List("vis"))

    lazy val `vnd.vividence.scriptfile`: MediaType =
      MediaType("application", "vnd.vividence.scriptfile", compressible = false, binary = true)

    lazy val `vnd.vocalshaper.vsp4`: MediaType =
      MediaType("application", "vnd.vocalshaper.vsp4", compressible = false, binary = true)

    lazy val `vnd.vsf`: MediaType =
      MediaType("application", "vnd.vsf", compressible = false, binary = true, fileExtensions = List("vsf"))

    lazy val `vnd.vuq`: MediaType =
      MediaType("application", "vnd.vuq", compressible = false, binary = true)

    lazy val `vnd.wantverse`: MediaType =
      MediaType("application", "vnd.wantverse", compressible = false, binary = true)

    lazy val `vnd.wap.sic`: MediaType =
      MediaType("application", "vnd.wap.sic", compressible = false, binary = true)

    lazy val `vnd.wap.slc`: MediaType =
      MediaType("application", "vnd.wap.slc", compressible = false, binary = true)

    lazy val `vnd.wap.wbxml`: MediaType =
      MediaType("application", "vnd.wap.wbxml", compressible = false, binary = true, fileExtensions = List("wbxml"))

    lazy val `vnd.wap.wmlc`: MediaType =
      MediaType("application", "vnd.wap.wmlc", compressible = false, binary = true, fileExtensions = List("wmlc"))

    lazy val `vnd.wap.wmlscriptc`: MediaType =
      MediaType(
        "application",
        "vnd.wap.wmlscriptc",
        compressible = false,
        binary = true,
        fileExtensions = List("wmlsc")
      )

    lazy val `vnd.wasmflow.wafl`: MediaType =
      MediaType("application", "vnd.wasmflow.wafl", compressible = false, binary = true)

    lazy val `vnd.webturbo`: MediaType =
      MediaType("application", "vnd.webturbo", compressible = false, binary = true, fileExtensions = List("wtb"))

    lazy val `vnd.wfa.dpp`: MediaType =
      MediaType("application", "vnd.wfa.dpp", compressible = false, binary = true)

    lazy val `vnd.wfa.p2p`: MediaType =
      MediaType("application", "vnd.wfa.p2p", compressible = false, binary = true)

    lazy val `vnd.wfa.wsc`: MediaType =
      MediaType("application", "vnd.wfa.wsc", compressible = false, binary = true)

    lazy val `vnd.windows.devicepairing`: MediaType =
      MediaType("application", "vnd.windows.devicepairing", compressible = false, binary = true)

    lazy val `vnd.wmap`: MediaType =
      MediaType("application", "vnd.wmap", compressible = false, binary = true)

    lazy val `vnd.wmc`: MediaType =
      MediaType("application", "vnd.wmc", compressible = false, binary = true)

    lazy val `vnd.wmf.bootstrap`: MediaType =
      MediaType("application", "vnd.wmf.bootstrap", compressible = false, binary = true)

    lazy val `vnd.wolfram.mathematica`: MediaType =
      MediaType("application", "vnd.wolfram.mathematica", compressible = false, binary = true)

    lazy val `vnd.wolfram.mathematica.package`: MediaType =
      MediaType("application", "vnd.wolfram.mathematica.package", compressible = false, binary = true)

    lazy val `vnd.wolfram.player`: MediaType =
      MediaType("application", "vnd.wolfram.player", compressible = false, binary = true, fileExtensions = List("nbp"))

    lazy val `vnd.wordlift`: MediaType =
      MediaType("application", "vnd.wordlift", compressible = false, binary = true)

    lazy val `vnd.wordperfect`: MediaType =
      MediaType("application", "vnd.wordperfect", compressible = false, binary = true, fileExtensions = List("wpd"))

    lazy val `vnd.wqd`: MediaType =
      MediaType("application", "vnd.wqd", compressible = false, binary = true, fileExtensions = List("wqd"))

    lazy val `vnd.wrq-hp3000-labelled`: MediaType =
      MediaType("application", "vnd.wrq-hp3000-labelled", compressible = false, binary = true)

    lazy val `vnd.wt.stf`: MediaType =
      MediaType("application", "vnd.wt.stf", compressible = false, binary = true, fileExtensions = List("stf"))

    lazy val `vnd.wv.csp+wbxml`: MediaType =
      MediaType("application", "vnd.wv.csp+wbxml", compressible = false, binary = true)

    lazy val `vnd.wv.csp+xml`: MediaType =
      MediaType("application", "vnd.wv.csp+xml", compressible = true, binary = true)

    lazy val `vnd.wv.ssp+xml`: MediaType =
      MediaType("application", "vnd.wv.ssp+xml", compressible = true, binary = true)

    lazy val `vnd.xacml+json`: MediaType =
      MediaType("application", "vnd.xacml+json", compressible = true, binary = false)

    lazy val `vnd.xara`: MediaType =
      MediaType("application", "vnd.xara", compressible = false, binary = true, fileExtensions = List("xar"))

    lazy val `vnd.xarin.cpj`: MediaType =
      MediaType("application", "vnd.xarin.cpj", compressible = false, binary = true)

    lazy val `vnd.xcdn`: MediaType =
      MediaType("application", "vnd.xcdn", compressible = false, binary = true)

    lazy val `vnd.xecrets-encrypted`: MediaType =
      MediaType("application", "vnd.xecrets-encrypted", compressible = false, binary = true)

    lazy val `vnd.xfdl`: MediaType =
      MediaType("application", "vnd.xfdl", compressible = false, binary = true, fileExtensions = List("xfdl"))

    lazy val `vnd.xfdl.webform`: MediaType =
      MediaType("application", "vnd.xfdl.webform", compressible = false, binary = true)

    lazy val `vnd.xmi+xml`: MediaType =
      MediaType("application", "vnd.xmi+xml", compressible = true, binary = true)

    lazy val `vnd.xmpie.cpkg`: MediaType =
      MediaType("application", "vnd.xmpie.cpkg", compressible = false, binary = true)

    lazy val `vnd.xmpie.dpkg`: MediaType =
      MediaType("application", "vnd.xmpie.dpkg", compressible = false, binary = true)

    lazy val `vnd.xmpie.plan`: MediaType =
      MediaType("application", "vnd.xmpie.plan", compressible = false, binary = true)

    lazy val `vnd.xmpie.ppkg`: MediaType =
      MediaType("application", "vnd.xmpie.ppkg", compressible = false, binary = true)

    lazy val `vnd.xmpie.xlim`: MediaType =
      MediaType("application", "vnd.xmpie.xlim", compressible = false, binary = true)

    lazy val `vnd.yamaha.hv-dic`: MediaType =
      MediaType("application", "vnd.yamaha.hv-dic", compressible = false, binary = true, fileExtensions = List("hvd"))

    lazy val `vnd.yamaha.hv-script`: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.hv-script",
        compressible = false,
        binary = true,
        fileExtensions = List("hvs")
      )

    lazy val `vnd.yamaha.hv-voice`: MediaType =
      MediaType("application", "vnd.yamaha.hv-voice", compressible = false, binary = true, fileExtensions = List("hvp"))

    lazy val `vnd.yamaha.openscoreformat`: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.openscoreformat",
        compressible = false,
        binary = true,
        fileExtensions = List("osf")
      )

    lazy val `vnd.yamaha.openscoreformat.osfpvg+xml`: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.openscoreformat.osfpvg+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("osfpvg")
      )

    lazy val `vnd.yamaha.remote-setup`: MediaType =
      MediaType("application", "vnd.yamaha.remote-setup", compressible = false, binary = true)

    lazy val `vnd.yamaha.smaf-audio`: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.smaf-audio",
        compressible = false,
        binary = true,
        fileExtensions = List("saf")
      )

    lazy val `vnd.yamaha.smaf-phrase`: MediaType =
      MediaType(
        "application",
        "vnd.yamaha.smaf-phrase",
        compressible = false,
        binary = true,
        fileExtensions = List("spf")
      )

    lazy val `vnd.yamaha.through-ngn`: MediaType =
      MediaType("application", "vnd.yamaha.through-ngn", compressible = false, binary = true)

    lazy val `vnd.yamaha.tunnel-udpencap`: MediaType =
      MediaType("application", "vnd.yamaha.tunnel-udpencap", compressible = false, binary = true)

    lazy val `vnd.yaoweme`: MediaType =
      MediaType("application", "vnd.yaoweme", compressible = false, binary = true)

    lazy val `vnd.yellowriver-custom-menu`: MediaType =
      MediaType(
        "application",
        "vnd.yellowriver-custom-menu",
        compressible = false,
        binary = true,
        fileExtensions = List("cmp")
      )

    lazy val `vnd.zoho-presentation.show`: MediaType =
      MediaType("application", "vnd.zoho-presentation.show", compressible = false, binary = true)

    lazy val `vnd.zul`: MediaType =
      MediaType("application", "vnd.zul", compressible = false, binary = true, fileExtensions = List("zir", "zirz"))

    lazy val `vnd.zzazz.deck+xml`: MediaType =
      MediaType("application", "vnd.zzazz.deck+xml", compressible = true, binary = true, fileExtensions = List("zaz"))

    lazy val `voicexml+xml`: MediaType =
      MediaType("application", "voicexml+xml", compressible = true, binary = true, fileExtensions = List("vxml"))

    lazy val `voucher-cms+json`: MediaType =
      MediaType("application", "voucher-cms+json", compressible = true, binary = false)

    lazy val `voucher-jws+json`: MediaType =
      MediaType("application", "voucher-jws+json", compressible = true, binary = false)

    lazy val `vp`: MediaType =
      MediaType("application", "vp", compressible = false, binary = true)

    lazy val `vp+cose`: MediaType =
      MediaType("application", "vp+cose", compressible = false, binary = true)

    lazy val `vp+jwt`: MediaType =
      MediaType("application", "vp+jwt", compressible = false, binary = true)

    lazy val `vp+sd-jwt`: MediaType =
      MediaType("application", "vp+sd-jwt", compressible = false, binary = true)

    lazy val `vq-rtcpxr`: MediaType =
      MediaType("application", "vq-rtcpxr", compressible = false, binary = true)

    lazy val `wasm`: MediaType =
      MediaType("application", "wasm", compressible = true, binary = true, fileExtensions = List("wasm"))

    lazy val `watcherinfo+xml`: MediaType =
      MediaType("application", "watcherinfo+xml", compressible = true, binary = true, fileExtensions = List("wif"))

    lazy val `webpush-options+json`: MediaType =
      MediaType("application", "webpush-options+json", compressible = true, binary = false)

    lazy val `whoispp-query`: MediaType =
      MediaType("application", "whoispp-query", compressible = false, binary = true)

    lazy val `whoispp-response`: MediaType =
      MediaType("application", "whoispp-response", compressible = false, binary = true)

    lazy val `widget`: MediaType =
      MediaType("application", "widget", compressible = false, binary = true, fileExtensions = List("wgt"))

    lazy val `winhlp`: MediaType =
      MediaType("application", "winhlp", compressible = false, binary = true, fileExtensions = List("hlp"))

    lazy val `wita`: MediaType =
      MediaType("application", "wita", compressible = false, binary = true)

    lazy val `wordperfect5.1`: MediaType =
      MediaType("application", "wordperfect5.1", compressible = false, binary = true)

    lazy val `wsdl+xml`: MediaType =
      MediaType("application", "wsdl+xml", compressible = true, binary = true, fileExtensions = List("wsdl"))

    lazy val `wspolicy+xml`: MediaType =
      MediaType("application", "wspolicy+xml", compressible = true, binary = true, fileExtensions = List("wspolicy"))

    lazy val `x-7z-compressed`: MediaType =
      MediaType("application", "x-7z-compressed", compressible = false, binary = true, fileExtensions = List("7z"))

    lazy val `x-abiword`: MediaType =
      MediaType("application", "x-abiword", compressible = false, binary = true, fileExtensions = List("abw"))

    lazy val `x-ace-compressed`: MediaType =
      MediaType("application", "x-ace-compressed", compressible = false, binary = true, fileExtensions = List("ace"))

    lazy val `x-amf`: MediaType =
      MediaType("application", "x-amf", compressible = false, binary = true)

    lazy val `x-apple-diskimage`: MediaType =
      MediaType("application", "x-apple-diskimage", compressible = false, binary = true, fileExtensions = List("dmg"))

    lazy val `x-arj`: MediaType =
      MediaType("application", "x-arj", compressible = false, binary = true, fileExtensions = List("arj"))

    lazy val `x-authorware-bin`: MediaType =
      MediaType(
        "application",
        "x-authorware-bin",
        compressible = false,
        binary = true,
        fileExtensions = List("aab", "x32", "u32", "vox")
      )

    lazy val `x-authorware-map`: MediaType =
      MediaType("application", "x-authorware-map", compressible = false, binary = true, fileExtensions = List("aam"))

    lazy val `x-authorware-seg`: MediaType =
      MediaType("application", "x-authorware-seg", compressible = false, binary = true, fileExtensions = List("aas"))

    lazy val `x-bcpio`: MediaType =
      MediaType("application", "x-bcpio", compressible = false, binary = true, fileExtensions = List("bcpio"))

    lazy val `x-bdoc`: MediaType =
      MediaType("application", "x-bdoc", compressible = false, binary = true, fileExtensions = List("bdoc"))

    lazy val `x-bittorrent`: MediaType =
      MediaType("application", "x-bittorrent", compressible = false, binary = true, fileExtensions = List("torrent"))

    lazy val `x-blender`: MediaType =
      MediaType("application", "x-blender", compressible = false, binary = true, fileExtensions = List("blend"))

    lazy val `x-blorb`: MediaType =
      MediaType("application", "x-blorb", compressible = false, binary = true, fileExtensions = List("blb", "blorb"))

    lazy val `x-bzip`: MediaType =
      MediaType("application", "x-bzip", compressible = false, binary = true, fileExtensions = List("bz"))

    lazy val `x-bzip2`: MediaType =
      MediaType("application", "x-bzip2", compressible = false, binary = true, fileExtensions = List("bz2", "boz"))

    lazy val `x-cbr`: MediaType =
      MediaType(
        "application",
        "x-cbr",
        compressible = false,
        binary = true,
        fileExtensions = List("cbr", "cba", "cbt", "cbz", "cb7")
      )

    lazy val `x-cdlink`: MediaType =
      MediaType("application", "x-cdlink", compressible = false, binary = true, fileExtensions = List("vcd"))

    lazy val `x-cfs-compressed`: MediaType =
      MediaType("application", "x-cfs-compressed", compressible = false, binary = true, fileExtensions = List("cfs"))

    lazy val `x-chat`: MediaType =
      MediaType("application", "x-chat", compressible = false, binary = true, fileExtensions = List("chat"))

    lazy val `x-chess-pgn`: MediaType =
      MediaType("application", "x-chess-pgn", compressible = false, binary = true, fileExtensions = List("pgn"))

    lazy val `x-chrome-extension`: MediaType =
      MediaType("application", "x-chrome-extension", compressible = false, binary = true, fileExtensions = List("crx"))

    lazy val `x-cocoa`: MediaType =
      MediaType("application", "x-cocoa", compressible = false, binary = true, fileExtensions = List("cco"))

    lazy val `x-compress`: MediaType =
      MediaType("application", "x-compress", compressible = false, binary = true)

    lazy val `x-compressed`: MediaType =
      MediaType("application", "x-compressed", compressible = false, binary = true, fileExtensions = List("rar"))

    lazy val `x-conference`: MediaType =
      MediaType("application", "x-conference", compressible = false, binary = true, fileExtensions = List("nsc"))

    lazy val `x-cpio`: MediaType =
      MediaType("application", "x-cpio", compressible = false, binary = true, fileExtensions = List("cpio"))

    lazy val `x-csh`: MediaType =
      MediaType("application", "x-csh", compressible = false, binary = true, fileExtensions = List("csh"))

    lazy val `x-deb`: MediaType =
      MediaType("application", "x-deb", compressible = false, binary = true)

    lazy val `x-debian-package`: MediaType =
      MediaType(
        "application",
        "x-debian-package",
        compressible = false,
        binary = true,
        fileExtensions = List("deb", "udeb")
      )

    lazy val `x-dgc-compressed`: MediaType =
      MediaType("application", "x-dgc-compressed", compressible = false, binary = true, fileExtensions = List("dgc"))

    lazy val `x-director`: MediaType =
      MediaType(
        "application",
        "x-director",
        compressible = false,
        binary = true,
        fileExtensions = List("dir", "dcr", "dxr", "cst", "cct", "cxt", "w3d", "fgd", "swa")
      )

    lazy val `x-doom`: MediaType =
      MediaType("application", "x-doom", compressible = false, binary = true, fileExtensions = List("wad"))

    lazy val `x-dtbncx+xml`: MediaType =
      MediaType("application", "x-dtbncx+xml", compressible = true, binary = true, fileExtensions = List("ncx"))

    lazy val `x-dtbook+xml`: MediaType =
      MediaType("application", "x-dtbook+xml", compressible = true, binary = true, fileExtensions = List("dtb"))

    lazy val `x-dtbresource+xml`: MediaType =
      MediaType("application", "x-dtbresource+xml", compressible = true, binary = true, fileExtensions = List("res"))

    lazy val `x-dvi`: MediaType =
      MediaType("application", "x-dvi", compressible = false, binary = true, fileExtensions = List("dvi"))

    lazy val `x-envoy`: MediaType =
      MediaType("application", "x-envoy", compressible = false, binary = true, fileExtensions = List("evy"))

    lazy val `x-eva`: MediaType =
      MediaType("application", "x-eva", compressible = false, binary = true, fileExtensions = List("eva"))

    lazy val `x-font-bdf`: MediaType =
      MediaType("application", "x-font-bdf", compressible = false, binary = true, fileExtensions = List("bdf"))

    lazy val `x-font-dos`: MediaType =
      MediaType("application", "x-font-dos", compressible = false, binary = true)

    lazy val `x-font-framemaker`: MediaType =
      MediaType("application", "x-font-framemaker", compressible = false, binary = true)

    lazy val `x-font-ghostscript`: MediaType =
      MediaType("application", "x-font-ghostscript", compressible = false, binary = true, fileExtensions = List("gsf"))

    lazy val `x-font-libgrx`: MediaType =
      MediaType("application", "x-font-libgrx", compressible = false, binary = true)

    lazy val `x-font-linux-psf`: MediaType =
      MediaType("application", "x-font-linux-psf", compressible = false, binary = true, fileExtensions = List("psf"))

    lazy val `x-font-pcf`: MediaType =
      MediaType("application", "x-font-pcf", compressible = false, binary = true, fileExtensions = List("pcf"))

    lazy val `x-font-snf`: MediaType =
      MediaType("application", "x-font-snf", compressible = false, binary = true, fileExtensions = List("snf"))

    lazy val `x-font-speedo`: MediaType =
      MediaType("application", "x-font-speedo", compressible = false, binary = true)

    lazy val `x-font-sunos-news`: MediaType =
      MediaType("application", "x-font-sunos-news", compressible = false, binary = true)

    lazy val `x-font-type1`: MediaType =
      MediaType(
        "application",
        "x-font-type1",
        compressible = false,
        binary = true,
        fileExtensions = List("pfa", "pfb", "pfm", "afm")
      )

    lazy val `x-font-vfont`: MediaType =
      MediaType("application", "x-font-vfont", compressible = false, binary = true)

    lazy val `x-freearc`: MediaType =
      MediaType("application", "x-freearc", compressible = false, binary = true, fileExtensions = List("arc"))

    lazy val `x-futuresplash`: MediaType =
      MediaType("application", "x-futuresplash", compressible = false, binary = true, fileExtensions = List("spl"))

    lazy val `x-gca-compressed`: MediaType =
      MediaType("application", "x-gca-compressed", compressible = false, binary = true, fileExtensions = List("gca"))

    lazy val `x-glulx`: MediaType =
      MediaType("application", "x-glulx", compressible = false, binary = true, fileExtensions = List("ulx"))

    lazy val `x-gnumeric`: MediaType =
      MediaType("application", "x-gnumeric", compressible = false, binary = true, fileExtensions = List("gnumeric"))

    lazy val `x-gramps-xml`: MediaType =
      MediaType("application", "x-gramps-xml", compressible = false, binary = true, fileExtensions = List("gramps"))

    lazy val `x-gtar`: MediaType =
      MediaType("application", "x-gtar", compressible = false, binary = true, fileExtensions = List("gtar"))

    lazy val `x-gzip`: MediaType =
      MediaType("application", "x-gzip", compressible = false, binary = true)

    lazy val `x-hdf`: MediaType =
      MediaType("application", "x-hdf", compressible = false, binary = true, fileExtensions = List("hdf"))

    lazy val `x-httpd-php`: MediaType =
      MediaType("application", "x-httpd-php", compressible = true, binary = true, fileExtensions = List("php"))

    lazy val `x-install-instructions`: MediaType =
      MediaType(
        "application",
        "x-install-instructions",
        compressible = false,
        binary = true,
        fileExtensions = List("install")
      )

    lazy val `x-ipynb+json`: MediaType =
      MediaType("application", "x-ipynb+json", compressible = true, binary = false, fileExtensions = List("ipynb"))

    lazy val `x-iso9660-image`: MediaType =
      MediaType("application", "x-iso9660-image", compressible = false, binary = true, fileExtensions = List("iso"))

    lazy val `x-iwork-keynote-sffkey`: MediaType =
      MediaType(
        "application",
        "x-iwork-keynote-sffkey",
        compressible = false,
        binary = true,
        fileExtensions = List("key")
      )

    lazy val `x-iwork-numbers-sffnumbers`: MediaType =
      MediaType(
        "application",
        "x-iwork-numbers-sffnumbers",
        compressible = false,
        binary = true,
        fileExtensions = List("numbers")
      )

    lazy val `x-iwork-pages-sffpages`: MediaType =
      MediaType(
        "application",
        "x-iwork-pages-sffpages",
        compressible = false,
        binary = true,
        fileExtensions = List("pages")
      )

    lazy val `x-java-archive-diff`: MediaType =
      MediaType(
        "application",
        "x-java-archive-diff",
        compressible = false,
        binary = true,
        fileExtensions = List("jardiff")
      )

    lazy val `x-java-jnlp-file`: MediaType =
      MediaType("application", "x-java-jnlp-file", compressible = false, binary = true, fileExtensions = List("jnlp"))

    lazy val `x-javascript`: MediaType =
      MediaType("application", "x-javascript", compressible = true, binary = false)

    lazy val `x-keepass2`: MediaType =
      MediaType("application", "x-keepass2", compressible = false, binary = true, fileExtensions = List("kdbx"))

    lazy val `x-latex`: MediaType =
      MediaType("application", "x-latex", compressible = false, binary = true, fileExtensions = List("latex"))

    lazy val `x-lua-bytecode`: MediaType =
      MediaType("application", "x-lua-bytecode", compressible = false, binary = true, fileExtensions = List("luac"))

    lazy val `x-lzh-compressed`: MediaType =
      MediaType(
        "application",
        "x-lzh-compressed",
        compressible = false,
        binary = true,
        fileExtensions = List("lzh", "lha")
      )

    lazy val `x-makeself`: MediaType =
      MediaType("application", "x-makeself", compressible = false, binary = true, fileExtensions = List("run"))

    lazy val `x-mie`: MediaType =
      MediaType("application", "x-mie", compressible = false, binary = true, fileExtensions = List("mie"))

    lazy val `x-mobipocket-ebook`: MediaType =
      MediaType(
        "application",
        "x-mobipocket-ebook",
        compressible = false,
        binary = true,
        fileExtensions = List("prc", "mobi")
      )

    lazy val `x-mpegurl`: MediaType =
      MediaType("application", "x-mpegurl", compressible = false, binary = true)

    lazy val `x-ms-application`: MediaType =
      MediaType(
        "application",
        "x-ms-application",
        compressible = false,
        binary = true,
        fileExtensions = List("application")
      )

    lazy val `x-ms-shortcut`: MediaType =
      MediaType("application", "x-ms-shortcut", compressible = false, binary = true, fileExtensions = List("lnk"))

    lazy val `x-ms-wmd`: MediaType =
      MediaType("application", "x-ms-wmd", compressible = false, binary = true, fileExtensions = List("wmd"))

    lazy val `x-ms-wmz`: MediaType =
      MediaType("application", "x-ms-wmz", compressible = false, binary = true, fileExtensions = List("wmz"))

    lazy val `x-ms-xbap`: MediaType =
      MediaType("application", "x-ms-xbap", compressible = false, binary = true, fileExtensions = List("xbap"))

    lazy val `x-msaccess`: MediaType =
      MediaType("application", "x-msaccess", compressible = false, binary = true, fileExtensions = List("mdb"))

    lazy val `x-msbinder`: MediaType =
      MediaType("application", "x-msbinder", compressible = false, binary = true, fileExtensions = List("obd"))

    lazy val `x-mscardfile`: MediaType =
      MediaType("application", "x-mscardfile", compressible = false, binary = true, fileExtensions = List("crd"))

    lazy val `x-msclip`: MediaType =
      MediaType("application", "x-msclip", compressible = false, binary = true, fileExtensions = List("clp"))

    lazy val `x-msdos-program`: MediaType =
      MediaType("application", "x-msdos-program", compressible = false, binary = true, fileExtensions = List("exe"))

    lazy val `x-msdownload`: MediaType =
      MediaType(
        "application",
        "x-msdownload",
        compressible = false,
        binary = true,
        fileExtensions = List("exe", "dll", "com", "bat", "msi")
      )

    lazy val `x-msmediaview`: MediaType =
      MediaType(
        "application",
        "x-msmediaview",
        compressible = false,
        binary = true,
        fileExtensions = List("mvb", "m13", "m14")
      )

    lazy val `x-msmetafile`: MediaType =
      MediaType(
        "application",
        "x-msmetafile",
        compressible = false,
        binary = true,
        fileExtensions = List("wmf", "wmz", "emf", "emz")
      )

    lazy val `x-msmoney`: MediaType =
      MediaType("application", "x-msmoney", compressible = false, binary = true, fileExtensions = List("mny"))

    lazy val `x-mspublisher`: MediaType =
      MediaType("application", "x-mspublisher", compressible = false, binary = true, fileExtensions = List("pub"))

    lazy val `x-msschedule`: MediaType =
      MediaType("application", "x-msschedule", compressible = false, binary = true, fileExtensions = List("scd"))

    lazy val `x-msterminal`: MediaType =
      MediaType("application", "x-msterminal", compressible = false, binary = true, fileExtensions = List("trm"))

    lazy val `x-mswrite`: MediaType =
      MediaType("application", "x-mswrite", compressible = false, binary = true, fileExtensions = List("wri"))

    lazy val `x-netcdf`: MediaType =
      MediaType("application", "x-netcdf", compressible = false, binary = true, fileExtensions = List("nc", "cdf"))

    lazy val `x-ns-proxy-autoconfig`: MediaType =
      MediaType(
        "application",
        "x-ns-proxy-autoconfig",
        compressible = true,
        binary = true,
        fileExtensions = List("pac")
      )

    lazy val `x-nzb`: MediaType =
      MediaType("application", "x-nzb", compressible = false, binary = true, fileExtensions = List("nzb"))

    lazy val `x-perl`: MediaType =
      MediaType("application", "x-perl", compressible = false, binary = true, fileExtensions = List("pl", "pm"))

    lazy val `x-pilot`: MediaType =
      MediaType("application", "x-pilot", compressible = false, binary = true, fileExtensions = List("prc", "pdb"))

    lazy val `x-pkcs12`: MediaType =
      MediaType("application", "x-pkcs12", compressible = false, binary = true, fileExtensions = List("p12", "pfx"))

    lazy val `x-pkcs7-certificates`: MediaType =
      MediaType(
        "application",
        "x-pkcs7-certificates",
        compressible = false,
        binary = true,
        fileExtensions = List("p7b", "spc")
      )

    lazy val `x-pkcs7-certreqresp`: MediaType =
      MediaType("application", "x-pkcs7-certreqresp", compressible = false, binary = true, fileExtensions = List("p7r"))

    lazy val `x-pki-message`: MediaType =
      MediaType("application", "x-pki-message", compressible = false, binary = true)

    lazy val `x-rar-compressed`: MediaType =
      MediaType("application", "x-rar-compressed", compressible = false, binary = true, fileExtensions = List("rar"))

    lazy val `x-redhat-package-manager`: MediaType =
      MediaType(
        "application",
        "x-redhat-package-manager",
        compressible = false,
        binary = true,
        fileExtensions = List("rpm")
      )

    lazy val `x-research-info-systems`: MediaType =
      MediaType(
        "application",
        "x-research-info-systems",
        compressible = false,
        binary = true,
        fileExtensions = List("ris")
      )

    lazy val `x-sea`: MediaType =
      MediaType("application", "x-sea", compressible = false, binary = true, fileExtensions = List("sea"))

    lazy val `x-sh`: MediaType =
      MediaType("application", "x-sh", compressible = true, binary = true, fileExtensions = List("sh"))

    lazy val `x-shar`: MediaType =
      MediaType("application", "x-shar", compressible = false, binary = true, fileExtensions = List("shar"))

    lazy val `x-shockwave-flash`: MediaType =
      MediaType("application", "x-shockwave-flash", compressible = false, binary = true, fileExtensions = List("swf"))

    lazy val `x-silverlight-app`: MediaType =
      MediaType("application", "x-silverlight-app", compressible = false, binary = true, fileExtensions = List("xap"))

    lazy val `x-sql`: MediaType =
      MediaType("application", "x-sql", compressible = false, binary = true, fileExtensions = List("sql"))

    lazy val `x-stuffit`: MediaType =
      MediaType("application", "x-stuffit", compressible = false, binary = true, fileExtensions = List("sit"))

    lazy val `x-stuffitx`: MediaType =
      MediaType("application", "x-stuffitx", compressible = false, binary = true, fileExtensions = List("sitx"))

    lazy val `x-subrip`: MediaType =
      MediaType("application", "x-subrip", compressible = false, binary = true, fileExtensions = List("srt"))

    lazy val `x-sv4cpio`: MediaType =
      MediaType("application", "x-sv4cpio", compressible = false, binary = true, fileExtensions = List("sv4cpio"))

    lazy val `x-sv4crc`: MediaType =
      MediaType("application", "x-sv4crc", compressible = false, binary = true, fileExtensions = List("sv4crc"))

    lazy val `x-t3vm-image`: MediaType =
      MediaType("application", "x-t3vm-image", compressible = false, binary = true, fileExtensions = List("t3"))

    lazy val `x-tads`: MediaType =
      MediaType("application", "x-tads", compressible = false, binary = true, fileExtensions = List("gam"))

    lazy val `x-tar`: MediaType =
      MediaType("application", "x-tar", compressible = true, binary = true, fileExtensions = List("tar"))

    lazy val `x-tcl`: MediaType =
      MediaType("application", "x-tcl", compressible = false, binary = true, fileExtensions = List("tcl", "tk"))

    lazy val `x-tex`: MediaType =
      MediaType("application", "x-tex", compressible = false, binary = true, fileExtensions = List("tex"))

    lazy val `x-tex-tfm`: MediaType =
      MediaType("application", "x-tex-tfm", compressible = false, binary = true, fileExtensions = List("tfm"))

    lazy val `x-texinfo`: MediaType =
      MediaType(
        "application",
        "x-texinfo",
        compressible = false,
        binary = true,
        fileExtensions = List("texinfo", "texi")
      )

    lazy val `x-tgif`: MediaType =
      MediaType("application", "x-tgif", compressible = false, binary = true, fileExtensions = List("obj"))

    lazy val `x-ustar`: MediaType =
      MediaType("application", "x-ustar", compressible = false, binary = true, fileExtensions = List("ustar"))

    lazy val `x-virtualbox-hdd`: MediaType =
      MediaType("application", "x-virtualbox-hdd", compressible = true, binary = true, fileExtensions = List("hdd"))

    lazy val `x-virtualbox-ova`: MediaType =
      MediaType("application", "x-virtualbox-ova", compressible = true, binary = true, fileExtensions = List("ova"))

    lazy val `x-virtualbox-ovf`: MediaType =
      MediaType("application", "x-virtualbox-ovf", compressible = true, binary = true, fileExtensions = List("ovf"))

    lazy val `x-virtualbox-vbox`: MediaType =
      MediaType("application", "x-virtualbox-vbox", compressible = true, binary = true, fileExtensions = List("vbox"))

    lazy val `x-virtualbox-vbox-extpack`: MediaType =
      MediaType(
        "application",
        "x-virtualbox-vbox-extpack",
        compressible = false,
        binary = true,
        fileExtensions = List("vbox-extpack")
      )

    lazy val `x-virtualbox-vdi`: MediaType =
      MediaType("application", "x-virtualbox-vdi", compressible = true, binary = true, fileExtensions = List("vdi"))

    lazy val `x-virtualbox-vhd`: MediaType =
      MediaType("application", "x-virtualbox-vhd", compressible = true, binary = true, fileExtensions = List("vhd"))

    lazy val `x-virtualbox-vmdk`: MediaType =
      MediaType("application", "x-virtualbox-vmdk", compressible = true, binary = true, fileExtensions = List("vmdk"))

    lazy val `x-wais-source`: MediaType =
      MediaType("application", "x-wais-source", compressible = false, binary = true, fileExtensions = List("src"))

    lazy val `x-web-app-manifest+json`: MediaType =
      MediaType(
        "application",
        "x-web-app-manifest+json",
        compressible = true,
        binary = false,
        fileExtensions = List("webapp")
      )

    lazy val `x-www-form-urlencoded`: MediaType =
      MediaType("application", "x-www-form-urlencoded", compressible = true, binary = true)

    lazy val `x-x509-ca-cert`: MediaType =
      MediaType(
        "application",
        "x-x509-ca-cert",
        compressible = false,
        binary = true,
        fileExtensions = List("der", "crt", "pem")
      )

    lazy val `x-x509-ca-ra-cert`: MediaType =
      MediaType("application", "x-x509-ca-ra-cert", compressible = false, binary = true)

    lazy val `x-x509-next-ca-cert`: MediaType =
      MediaType("application", "x-x509-next-ca-cert", compressible = false, binary = true)

    lazy val `x-xfig`: MediaType =
      MediaType("application", "x-xfig", compressible = false, binary = true, fileExtensions = List("fig"))

    lazy val `x-xliff+xml`: MediaType =
      MediaType("application", "x-xliff+xml", compressible = true, binary = true, fileExtensions = List("xlf"))

    lazy val `x-xpinstall`: MediaType =
      MediaType("application", "x-xpinstall", compressible = false, binary = true, fileExtensions = List("xpi"))

    lazy val `x-xz`: MediaType =
      MediaType("application", "x-xz", compressible = false, binary = true, fileExtensions = List("xz"))

    lazy val `x-zip-compressed`: MediaType =
      MediaType("application", "x-zip-compressed", compressible = false, binary = true, fileExtensions = List("zip"))

    lazy val `x-zmachine`: MediaType =
      MediaType(
        "application",
        "x-zmachine",
        compressible = false,
        binary = true,
        fileExtensions = List("z1", "z2", "z3", "z4", "z5", "z6", "z7", "z8")
      )

    lazy val `x400-bp`: MediaType =
      MediaType("application", "x400-bp", compressible = false, binary = true)

    lazy val `xacml+xml`: MediaType =
      MediaType("application", "xacml+xml", compressible = true, binary = true)

    lazy val `xaml+xml`: MediaType =
      MediaType("application", "xaml+xml", compressible = true, binary = true, fileExtensions = List("xaml"))

    lazy val `xcap-att+xml`: MediaType =
      MediaType("application", "xcap-att+xml", compressible = true, binary = true, fileExtensions = List("xav"))

    lazy val `xcap-caps+xml`: MediaType =
      MediaType("application", "xcap-caps+xml", compressible = true, binary = true, fileExtensions = List("xca"))

    lazy val `xcap-diff+xml`: MediaType =
      MediaType("application", "xcap-diff+xml", compressible = true, binary = true, fileExtensions = List("xdf"))

    lazy val `xcap-el+xml`: MediaType =
      MediaType("application", "xcap-el+xml", compressible = true, binary = true, fileExtensions = List("xel"))

    lazy val `xcap-error+xml`: MediaType =
      MediaType("application", "xcap-error+xml", compressible = true, binary = true)

    lazy val `xcap-ns+xml`: MediaType =
      MediaType("application", "xcap-ns+xml", compressible = true, binary = true, fileExtensions = List("xns"))

    lazy val `xcon-conference-info+xml`: MediaType =
      MediaType("application", "xcon-conference-info+xml", compressible = true, binary = true)

    lazy val `xcon-conference-info-diff+xml`: MediaType =
      MediaType("application", "xcon-conference-info-diff+xml", compressible = true, binary = true)

    lazy val `xenc+xml`: MediaType =
      MediaType("application", "xenc+xml", compressible = true, binary = true, fileExtensions = List("xenc"))

    lazy val `xfdf`: MediaType =
      MediaType("application", "xfdf", compressible = false, binary = true, fileExtensions = List("xfdf"))

    lazy val `xhtml+xml`: MediaType =
      MediaType("application", "xhtml+xml", compressible = true, binary = true, fileExtensions = List("xhtml", "xht"))

    lazy val `xhtml-voice+xml`: MediaType =
      MediaType("application", "xhtml-voice+xml", compressible = true, binary = true)

    lazy val `xliff+xml`: MediaType =
      MediaType("application", "xliff+xml", compressible = true, binary = true, fileExtensions = List("xlf"))

    lazy val `xml`: MediaType =
      MediaType(
        "application",
        "xml",
        compressible = true,
        binary = false,
        fileExtensions = List("xml", "xsl", "xsd", "rng")
      )

    lazy val `xml-dtd`: MediaType =
      MediaType("application", "xml-dtd", compressible = true, binary = false, fileExtensions = List("dtd"))

    lazy val `xml-external-parsed-entity`: MediaType =
      MediaType("application", "xml-external-parsed-entity", compressible = false, binary = false)

    lazy val `xml-patch+xml`: MediaType =
      MediaType("application", "xml-patch+xml", compressible = true, binary = false)

    lazy val `xmpp+xml`: MediaType =
      MediaType("application", "xmpp+xml", compressible = true, binary = true)

    lazy val `xop+xml`: MediaType =
      MediaType("application", "xop+xml", compressible = true, binary = true, fileExtensions = List("xop"))

    lazy val `xproc+xml`: MediaType =
      MediaType("application", "xproc+xml", compressible = true, binary = true, fileExtensions = List("xpl"))

    lazy val `xslt+xml`: MediaType =
      MediaType("application", "xslt+xml", compressible = true, binary = true, fileExtensions = List("xsl", "xslt"))

    lazy val `xspf+xml`: MediaType =
      MediaType("application", "xspf+xml", compressible = true, binary = true, fileExtensions = List("xspf"))

    lazy val `xv+xml`: MediaType =
      MediaType(
        "application",
        "xv+xml",
        compressible = true,
        binary = true,
        fileExtensions = List("mxml", "xhvml", "xvml", "xvm")
      )

    lazy val `yaml`: MediaType =
      MediaType("application", "yaml", compressible = true, binary = true)

    lazy val `yang`: MediaType =
      MediaType("application", "yang", compressible = false, binary = true, fileExtensions = List("yang"))

    lazy val `yang-data+cbor`: MediaType =
      MediaType("application", "yang-data+cbor", compressible = false, binary = true)

    lazy val `yang-data+json`: MediaType =
      MediaType("application", "yang-data+json", compressible = true, binary = false)

    lazy val `yang-data+xml`: MediaType =
      MediaType("application", "yang-data+xml", compressible = true, binary = true)

    lazy val `yang-patch+json`: MediaType =
      MediaType("application", "yang-patch+json", compressible = true, binary = false)

    lazy val `yang-patch+xml`: MediaType =
      MediaType("application", "yang-patch+xml", compressible = true, binary = true)

    lazy val `yang-sid+json`: MediaType =
      MediaType("application", "yang-sid+json", compressible = true, binary = false)

    lazy val `yin+xml`: MediaType =
      MediaType("application", "yin+xml", compressible = true, binary = true, fileExtensions = List("yin"))

    lazy val `zip`: MediaType =
      MediaType("application", "zip", compressible = false, binary = true, fileExtensions = List("zip"))

    lazy val `zip+dotlottie`: MediaType =
      MediaType("application", "zip+dotlottie", compressible = false, binary = true, fileExtensions = List("lottie"))

    lazy val `zlib`: MediaType =
      MediaType("application", "zlib", compressible = false, binary = true)

    lazy val `zstd`: MediaType =
      MediaType("application", "zstd", compressible = false, binary = true)

    lazy val all: List[MediaType] = List(
      `1d-interleaved-parityfec`,
      `3gpdash-qoe-report+xml`,
      `3gpp-ims+xml`,
      `3gpp-mbs-object-manifest+json`,
      `3gpp-mbs-user-service-descriptions+json`,
      `3gpp-media-delivery-metrics-report+json`,
      `3gpphal+json`,
      `3gpphalforms+json`,
      `a2l`,
      `ace+cbor`,
      `ace+json`,
      `ace-groupcomm+cbor`,
      `ace-trl+cbor`,
      `activemessage`,
      `activity+json`,
      `aif+cbor`,
      `aif+json`,
      `alto-cdni+json`,
      `alto-cdnifilter+json`,
      `alto-costmap+json`,
      `alto-costmapfilter+json`,
      `alto-directory+json`,
      `alto-endpointcost+json`,
      `alto-endpointcostparams+json`,
      `alto-endpointprop+json`,
      `alto-endpointpropparams+json`,
      `alto-error+json`,
      `alto-networkmap+json`,
      `alto-networkmapfilter+json`,
      `alto-propmap+json`,
      `alto-propmapparams+json`,
      `alto-tips+json`,
      `alto-tipsparams+json`,
      `alto-updatestreamcontrol+json`,
      `alto-updatestreamparams+json`,
      `aml`,
      `andrew-inset`,
      `appinstaller`,
      `applefile`,
      `applixware`,
      `appx`,
      `appxbundle`,
      `asyncapi+json`,
      `asyncapi+yaml`,
      `at+jwt`,
      `atf`,
      `atfx`,
      `atom+xml`,
      `atomcat+xml`,
      `atomdeleted+xml`,
      `atomicmail`,
      `atomsvc+xml`,
      `atsc-dwd+xml`,
      `atsc-dynamic-event-message`,
      `atsc-held+xml`,
      `atsc-rdt+json`,
      `atsc-rsat+xml`,
      `atxml`,
      `auth-policy+xml`,
      `automationml-aml+xml`,
      `automationml-amlx+zip`,
      `bacnet-xdd+zip`,
      `batch-smtp`,
      `bdoc`,
      `beep+xml`,
      `bufr`,
      `c2pa`,
      `calendar+json`,
      `calendar+xml`,
      `call-completion`,
      `cals-1840`,
      `captive+json`,
      `cbor`,
      `cbor-seq`,
      `cccex`,
      `ccmp+xml`,
      `ccxml+xml`,
      `cda+xml`,
      `cdfx+xml`,
      `cdmi-capability`,
      `cdmi-container`,
      `cdmi-domain`,
      `cdmi-object`,
      `cdmi-queue`,
      `cdni`,
      `ce+cbor`,
      `cea`,
      `cea-2018+xml`,
      `cellml+xml`,
      `cfw`,
      `cid`,
      `cid-edhoc+cbor-seq`,
      `city+json`,
      `city+json-seq`,
      `clr`,
      `clue+xml`,
      `clue_info+xml`,
      `cms`,
      `cmw+cbor`,
      `cmw+cose`,
      `cmw+json`,
      `cmw+jws`,
      `cnrp+xml`,
      `coap-eap`,
      `coap-group+json`,
      `coap-payload`,
      `commonground`,
      `concise-problem-details+cbor`,
      `conference-info+xml`,
      `cose`,
      `cose-key`,
      `cose-key-set`,
      `cose-x509`,
      `cpl+xml`,
      `csrattrs`,
      `csta+xml`,
      `cstadata+xml`,
      `csvm+json`,
      `cu-seeme`,
      `cwl`,
      `cwl+json`,
      `cwl+yaml`,
      `cwt`,
      `cybercash`,
      `dart`,
      `dash+xml`,
      `dash-patch+xml`,
      `dashdelta`,
      `davmount+xml`,
      `dca-rft`,
      `dcd`,
      `dec-dx`,
      `dialog-info+xml`,
      `dicom`,
      `dicom+json`,
      `dicom+xml`,
      `did`,
      `dii`,
      `dit`,
      `dns`,
      `dns+json`,
      `dns-message`,
      `docbook+xml`,
      `dots+cbor`,
      `dpop+jwt`,
      `dskpp+xml`,
      `dssc+der`,
      `dssc+xml`,
      `dvcs`,
      `eat+cwt`,
      `eat+jwt`,
      `eat-bun+cbor`,
      `eat-bun+json`,
      `eat-ucs+cbor`,
      `eat-ucs+json`,
      `ecmascript`,
      `edhoc+cbor-seq`,
      `edi-consent`,
      `edi-x12`,
      `edifact`,
      `efi`,
      `elm+json`,
      `elm+xml`,
      `emergencycalldata.cap+xml`,
      `emergencycalldata.comment+xml`,
      `emergencycalldata.control+xml`,
      `emergencycalldata.deviceinfo+xml`,
      `emergencycalldata.ecall.msd`,
      `emergencycalldata.legacyesn+json`,
      `emergencycalldata.providerinfo+xml`,
      `emergencycalldata.serviceinfo+xml`,
      `emergencycalldata.subscriberinfo+xml`,
      `emergencycalldata.veds+xml`,
      `emma+xml`,
      `emotionml+xml`,
      `encaprtp`,
      `entity-statement+jwt`,
      `epp+xml`,
      `epub+zip`,
      `eshop`,
      `exi`,
      `expect-ct-report+json`,
      `express`,
      `fastinfoset`,
      `fastsoap`,
      `fdf`,
      `fdt+xml`,
      `fhir+json`,
      `fhir+xml`,
      `fido.trusted-apps+json`,
      `fits`,
      `flexfec`,
      `font-sfnt`,
      `font-tdpfr`,
      `font-woff`,
      `framework-attributes+xml`,
      `geo+json`,
      `geo+json-seq`,
      `geofeed+csv`,
      `geopackage+sqlite3`,
      `geopose+json`,
      `geoxacml+json`,
      `geoxacml+xml`,
      `gltf-buffer`,
      `gml+xml`,
      `gnap-binding-jws`,
      `gnap-binding-jwsd`,
      `gnap-binding-rotation-jws`,
      `gnap-binding-rotation-jwsd`,
      `gpx+xml`,
      `grib`,
      `gxf`,
      `gzip`,
      `h224`,
      `held+xml`,
      `hjson`,
      `hl7v2+xml`,
      `http`,
      `hyperstudio`,
      `ibe-key-request+xml`,
      `ibe-pkg-reply+xml`,
      `ibe-pp-data`,
      `iges`,
      `im-iscomposing+xml`,
      `index`,
      `index.cmd`,
      `index.obj`,
      `index.response`,
      `index.vnd`,
      `inkml+xml`,
      `iotp`,
      `ipfix`,
      `ipp`,
      `isup`,
      `its+xml`,
      `java-archive`,
      `java-serialized-object`,
      `java-vm`,
      `javascript`,
      `jf2feed+json`,
      `jose`,
      `jose+json`,
      `jrd+json`,
      `jscalendar+json`,
      `jscontact+json`,
      `json`,
      `json-patch+json`,
      `json-patch-query+json`,
      `json-seq`,
      `json5`,
      `jsonml+json`,
      `jsonpath`,
      `jwk+json`,
      `jwk-set+json`,
      `jwk-set+jwt`,
      `jwt`,
      `kb+jwt`,
      `kbl+xml`,
      `kpml-request+xml`,
      `kpml-response+xml`,
      `ld+json`,
      `lgr+xml`,
      `link-format`,
      `linkset`,
      `linkset+json`,
      `load-control+xml`,
      `logout+jwt`,
      `lost+xml`,
      `lostsync+xml`,
      `lpf+zip`,
      `lxf`,
      `mac-binhex40`,
      `mac-compactpro`,
      `macwriteii`,
      `mads+xml`,
      `manifest+json`,
      `marc`,
      `marcxml+xml`,
      `mathematica`,
      `mathml+xml`,
      `mathml-content+xml`,
      `mathml-presentation+xml`,
      `mbms-associated-procedure-description+xml`,
      `mbms-deregister+xml`,
      `mbms-envelope+xml`,
      `mbms-msk+xml`,
      `mbms-msk-response+xml`,
      `mbms-protection-description+xml`,
      `mbms-reception-report+xml`,
      `mbms-register+xml`,
      `mbms-register-response+xml`,
      `mbms-schedule+xml`,
      `mbms-user-service-description+xml`,
      `mbox`,
      `media-policy-dataset+xml`,
      `media_control+xml`,
      `mediaservercontrol+xml`,
      `merge-patch+json`,
      `metalink+xml`,
      `metalink4+xml`,
      `mets+xml`,
      `mf4`,
      `mikey`,
      `mipc`,
      `missing-blocks+cbor-seq`,
      `mmt-aei+xml`,
      `mmt-usd+xml`,
      `mods+xml`,
      `moss-keys`,
      `moss-signature`,
      `mosskey-data`,
      `mosskey-request`,
      `mp21`,
      `mp4`,
      `mpeg4-generic`,
      `mpeg4-iod`,
      `mpeg4-iod-xmt`,
      `mrb-consumer+xml`,
      `mrb-publish+xml`,
      `msc-ivr+xml`,
      `msc-mixer+xml`,
      `msix`,
      `msixbundle`,
      `msword`,
      `mud+json`,
      `multipart-core`,
      `mxf`,
      `n-quads`,
      `n-triples`,
      `nasdata`,
      `news-checkgroups`,
      `news-groupinfo`,
      `news-transmission`,
      `nlsml+xml`,
      `node`,
      `nss`,
      `oauth-authz-req+jwt`,
      `oblivious-dns-message`,
      `ocsp-request`,
      `ocsp-response`,
      `octet-stream`,
      `oda`,
      `odm+xml`,
      `odx`,
      `oebps-package+xml`,
      `ogg`,
      `ohttp-keys`,
      `omdoc+xml`,
      `onenote`,
      `opc-nodeset+xml`,
      `oscore`,
      `oxps`,
      `p21`,
      `p21+zip`,
      `p2p-overlay+xml`,
      `parityfec`,
      `passport`,
      `patch-ops-error+xml`,
      `pdf`,
      `pdx`,
      `pem-certificate-chain`,
      `pgp-encrypted`,
      `pgp-keys`,
      `pgp-signature`,
      `pics-rules`,
      `pidf+xml`,
      `pidf-diff+xml`,
      `pkcs10`,
      `pkcs12`,
      `pkcs7-mime`,
      `pkcs7-signature`,
      `pkcs8`,
      `pkcs8-encrypted`,
      `pkix-attr-cert`,
      `pkix-cert`,
      `pkix-crl`,
      `pkix-pkipath`,
      `pkixcmp`,
      `pls+xml`,
      `poc-settings+xml`,
      `postscript`,
      `ppsp-tracker+json`,
      `private-token-issuer-directory`,
      `private-token-request`,
      `private-token-response`,
      `problem+json`,
      `problem+xml`,
      `protobuf`,
      `protobuf+json`,
      `provenance+xml`,
      `provided-claims+jwt`,
      `prs.alvestrand.titrax-sheet`,
      `prs.cww`,
      `prs.cyn`,
      `prs.hpub+zip`,
      `prs.implied-document+xml`,
      `prs.implied-executable`,
      `prs.implied-object+json`,
      `prs.implied-object+json-seq`,
      `prs.implied-object+yaml`,
      `prs.implied-structure`,
      `prs.mayfile`,
      `prs.nprend`,
      `prs.plucker`,
      `prs.rdf-xml-crypt`,
      `prs.sclt`,
      `prs.vcfbzip2`,
      `prs.xsf+xml`,
      `pskc+xml`,
      `pvd+json`,
      `qsig`,
      `raml+yaml`,
      `raptorfec`,
      `rdap+json`,
      `rdf+xml`,
      `reginfo+xml`,
      `relax-ng-compact-syntax`,
      `remote-printing`,
      `reputon+json`,
      `resolve-response+jwt`,
      `resource-lists+xml`,
      `resource-lists-diff+xml`,
      `rfc+xml`,
      `riscos`,
      `rlmi+xml`,
      `rls-services+xml`,
      `route-apd+xml`,
      `route-s-tsid+xml`,
      `route-usd+xml`,
      `rpki-checklist`,
      `rpki-ghostbusters`,
      `rpki-manifest`,
      `rpki-publication`,
      `rpki-roa`,
      `rpki-signed-tal`,
      `rpki-updown`,
      `rs-metadata+xml`,
      `rsd+xml`,
      `rss+xml`,
      `rtf`,
      `rtploopback`,
      `rtx`,
      `samlassertion+xml`,
      `samlmetadata+xml`,
      `sarif+json`,
      `sarif-external-properties+json`,
      `sbe`,
      `sbml+xml`,
      `scaip+xml`,
      `scim+json`,
      `scitt-receipt+cose`,
      `scitt-statement+cose`,
      `scvp-cv-request`,
      `scvp-cv-response`,
      `scvp-vp-request`,
      `scvp-vp-response`,
      `sd-jwt`,
      `sd-jwt+json`,
      `sdf+json`,
      `sdp`,
      `secevent+jwt`,
      `senml+cbor`,
      `senml+json`,
      `senml+xml`,
      `senml-etch+cbor`,
      `senml-etch+json`,
      `senml-exi`,
      `sensml+cbor`,
      `sensml+json`,
      `sensml+xml`,
      `sensml-exi`,
      `sep+xml`,
      `sep-exi`,
      `session-info`,
      `set-payment`,
      `set-payment-initiation`,
      `set-registration`,
      `set-registration-initiation`,
      `sgml`,
      `sgml-open-catalog`,
      `shf+xml`,
      `sieve`,
      `simple-filter+xml`,
      `simple-message-summary`,
      `simplesymbolcontainer`,
      `sipc`,
      `slate`,
      `smil`,
      `smil+xml`,
      `smpte336m`,
      `soap+fastinfoset`,
      `soap+xml`,
      `sparql-query`,
      `sparql-results+xml`,
      `spdx+json`,
      `spirits-event+xml`,
      `sql`,
      `srgs`,
      `srgs+xml`,
      `sru+xml`,
      `ssdl+xml`,
      `sslkeylogfile`,
      `ssml+xml`,
      `st2110-41`,
      `stix+json`,
      `stratum`,
      `suit-envelope+cose`,
      `suit-report+cose`,
      `swid+cbor`,
      `swid+xml`,
      `tamp-apex-update`,
      `tamp-apex-update-confirm`,
      `tamp-community-update`,
      `tamp-community-update-confirm`,
      `tamp-error`,
      `tamp-sequence-adjust`,
      `tamp-sequence-adjust-confirm`,
      `tamp-status-query`,
      `tamp-status-response`,
      `tamp-update`,
      `tamp-update-confirm`,
      `tar`,
      `taxii+json`,
      `td+json`,
      `tei+xml`,
      `tetra_isi`,
      `texinfo`,
      `thraud+xml`,
      `timestamp-query`,
      `timestamp-reply`,
      `timestamped-data`,
      `tlsrpt+gzip`,
      `tlsrpt+json`,
      `tm+json`,
      `tnauthlist`,
      `toc+cbor`,
      `token-introspection+jwt`,
      `toml`,
      `trickle-ice-sdpfrag`,
      `trig`,
      `trust-chain+json`,
      `trust-mark+jwt`,
      `trust-mark-delegation+jwt`,
      `ttml+xml`,
      `tve-trigger`,
      `tzif`,
      `tzif-leap`,
      `ubjson`,
      `uccs+cbor`,
      `ujcs+json`,
      `ulpfec`,
      `urc-grpsheet+xml`,
      `urc-ressheet+xml`,
      `urc-targetdesc+xml`,
      `urc-uisocketdesc+xml`,
      `vc`,
      `vc+cose`,
      `vc+jwt`,
      `vc+sd-jwt`,
      `vcard+json`,
      `vcard+xml`,
      `vec+xml`,
      `vec-package+gzip`,
      `vec-package+zip`,
      `vemmi`,
      `vividence.scriptfile`,
      `vnd.1000minds.decision-model+xml`,
      `vnd.1ob`,
      `vnd.3gpp-prose+xml`,
      `vnd.3gpp-prose-pc3a+xml`,
      `vnd.3gpp-prose-pc3ach+xml`,
      `vnd.3gpp-prose-pc3ch+xml`,
      `vnd.3gpp-prose-pc8+xml`,
      `vnd.3gpp-v2x-local-service-information`,
      `vnd.3gpp.5gnas`,
      `vnd.3gpp.5gsa2x`,
      `vnd.3gpp.5gsa2x-local-service-information`,
      `vnd.3gpp.5gsv2x`,
      `vnd.3gpp.5gsv2x-local-service-information`,
      `vnd.3gpp.access-transfer-events+xml`,
      `vnd.3gpp.bsf+xml`,
      `vnd.3gpp.crs+xml`,
      `vnd.3gpp.current-location-discovery+xml`,
      `vnd.3gpp.gmop+xml`,
      `vnd.3gpp.gtpc`,
      `vnd.3gpp.interworking-data`,
      `vnd.3gpp.lpp`,
      `vnd.3gpp.mc-signalling-ear`,
      `vnd.3gpp.mcdata-affiliation-command+xml`,
      `vnd.3gpp.mcdata-info+xml`,
      `vnd.3gpp.mcdata-msgstore-ctrl-request+xml`,
      `vnd.3gpp.mcdata-payload`,
      `vnd.3gpp.mcdata-regroup+xml`,
      `vnd.3gpp.mcdata-service-config+xml`,
      `vnd.3gpp.mcdata-signalling`,
      `vnd.3gpp.mcdata-ue-config+xml`,
      `vnd.3gpp.mcdata-user-profile+xml`,
      `vnd.3gpp.mcptt-affiliation-command+xml`,
      `vnd.3gpp.mcptt-floor-request+xml`,
      `vnd.3gpp.mcptt-info+xml`,
      `vnd.3gpp.mcptt-location-info+xml`,
      `vnd.3gpp.mcptt-mbms-usage-info+xml`,
      `vnd.3gpp.mcptt-regroup+xml`,
      `vnd.3gpp.mcptt-service-config+xml`,
      `vnd.3gpp.mcptt-signed+xml`,
      `vnd.3gpp.mcptt-ue-config+xml`,
      `vnd.3gpp.mcptt-ue-init-config+xml`,
      `vnd.3gpp.mcptt-user-profile+xml`,
      `vnd.3gpp.mcvideo-affiliation-command+xml`,
      `vnd.3gpp.mcvideo-info+xml`,
      `vnd.3gpp.mcvideo-location-info+xml`,
      `vnd.3gpp.mcvideo-mbms-usage-info+xml`,
      `vnd.3gpp.mcvideo-regroup+xml`,
      `vnd.3gpp.mcvideo-service-config+xml`,
      `vnd.3gpp.mcvideo-transmission-request+xml`,
      `vnd.3gpp.mcvideo-ue-config+xml`,
      `vnd.3gpp.mcvideo-user-profile+xml`,
      `vnd.3gpp.mid-call+xml`,
      `vnd.3gpp.ngap`,
      `vnd.3gpp.pfcp`,
      `vnd.3gpp.pic-bw-large`,
      `vnd.3gpp.pic-bw-small`,
      `vnd.3gpp.pic-bw-var`,
      `vnd.3gpp.pinapp-info+xml`,
      `vnd.3gpp.s1ap`,
      `vnd.3gpp.seal-app-comm-requirements-info+xml`,
      `vnd.3gpp.seal-data-delivery-info+cbor`,
      `vnd.3gpp.seal-data-delivery-info+xml`,
      `vnd.3gpp.seal-group-doc+xml`,
      `vnd.3gpp.seal-info+xml`,
      `vnd.3gpp.seal-location-info+cbor`,
      `vnd.3gpp.seal-location-info+xml`,
      `vnd.3gpp.seal-mbms-usage-info+xml`,
      `vnd.3gpp.seal-mbs-usage-info+xml`,
      `vnd.3gpp.seal-network-qos-management-info+xml`,
      `vnd.3gpp.seal-network-resource-info+cbor`,
      `vnd.3gpp.seal-ue-config-info+xml`,
      `vnd.3gpp.seal-unicast-info+xml`,
      `vnd.3gpp.seal-user-profile-info+xml`,
      `vnd.3gpp.sms`,
      `vnd.3gpp.sms+xml`,
      `vnd.3gpp.srvcc-ext+xml`,
      `vnd.3gpp.srvcc-info+xml`,
      `vnd.3gpp.state-and-event-info+xml`,
      `vnd.3gpp.ussd+xml`,
      `vnd.3gpp.v2x`,
      `vnd.3gpp.vae-info+xml`,
      `vnd.3gpp2.bcmcsinfo+xml`,
      `vnd.3gpp2.sms`,
      `vnd.3gpp2.tcap`,
      `vnd.3lightssoftware.imagescal`,
      `vnd.3m.post-it-notes`,
      `vnd.accpac.simply.aso`,
      `vnd.accpac.simply.imp`,
      `vnd.acm.addressxfer+json`,
      `vnd.acm.chatbot+json`,
      `vnd.acucobol`,
      `vnd.acucorp`,
      `vnd.adobe.air-application-installer-package+zip`,
      `vnd.adobe.flash.movie`,
      `vnd.adobe.formscentral.fcdt`,
      `vnd.adobe.fxp`,
      `vnd.adobe.partial-upload`,
      `vnd.adobe.xdp+xml`,
      `vnd.adobe.xfdf`,
      `vnd.aether.imp`,
      `vnd.afpc.afplinedata`,
      `vnd.afpc.afplinedata-pagedef`,
      `vnd.afpc.cmoca-cmresource`,
      `vnd.afpc.foca-charset`,
      `vnd.afpc.foca-codedfont`,
      `vnd.afpc.foca-codepage`,
      `vnd.afpc.modca`,
      `vnd.afpc.modca-cmtable`,
      `vnd.afpc.modca-formdef`,
      `vnd.afpc.modca-mediummap`,
      `vnd.afpc.modca-objectcontainer`,
      `vnd.afpc.modca-overlay`,
      `vnd.afpc.modca-pagesegment`,
      `vnd.age`,
      `vnd.ah-barcode`,
      `vnd.ahead.space`,
      `vnd.aia`,
      `vnd.airzip.filesecure.azf`,
      `vnd.airzip.filesecure.azs`,
      `vnd.amadeus+json`,
      `vnd.amazon.ebook`,
      `vnd.amazon.mobi8-ebook`,
      `vnd.americandynamics.acc`,
      `vnd.amiga.ami`,
      `vnd.amundsen.maze+xml`,
      `vnd.android.ota`,
      `vnd.android.package-archive`,
      `vnd.anki`,
      `vnd.anser-web-certificate-issue-initiation`,
      `vnd.anser-web-funds-transfer-initiation`,
      `vnd.antix.game-component`,
      `vnd.apache.arrow.file`,
      `vnd.apache.arrow.stream`,
      `vnd.apache.parquet`,
      `vnd.apache.thrift.binary`,
      `vnd.apache.thrift.compact`,
      `vnd.apache.thrift.json`,
      `vnd.apexlang`,
      `vnd.api+json`,
      `vnd.aplextor.warrp+json`,
      `vnd.apothekende.reservation+json`,
      `vnd.apple.installer+xml`,
      `vnd.apple.keynote`,
      `vnd.apple.mpegurl`,
      `vnd.apple.numbers`,
      `vnd.apple.pages`,
      `vnd.apple.pkpass`,
      `vnd.arastra.swi`,
      `vnd.aristanetworks.swi`,
      `vnd.artisan+json`,
      `vnd.artsquare`,
      `vnd.as207960.vas.config+jer`,
      `vnd.as207960.vas.config+uper`,
      `vnd.as207960.vas.tap+jer`,
      `vnd.as207960.vas.tap+uper`,
      `vnd.astraea-software.iota`,
      `vnd.audiograph`,
      `vnd.autodesk.fbx`,
      `vnd.autopackage`,
      `vnd.avalon+json`,
      `vnd.avistar+xml`,
      `vnd.balsamiq.bmml+xml`,
      `vnd.balsamiq.bmpr`,
      `vnd.banana-accounting`,
      `vnd.bbf.usp.error`,
      `vnd.bbf.usp.msg`,
      `vnd.bbf.usp.msg+json`,
      `vnd.bekitzur-stech+json`,
      `vnd.belightsoft.lhzd+zip`,
      `vnd.belightsoft.lhzl+zip`,
      `vnd.bint.med-content`,
      `vnd.biopax.rdf+xml`,
      `vnd.blink-idb-value-wrapper`,
      `vnd.blueice.multipass`,
      `vnd.bluetooth.ep.oob`,
      `vnd.bluetooth.le.oob`,
      `vnd.bmi`,
      `vnd.bpf`,
      `vnd.bpf3`,
      `vnd.businessobjects`,
      `vnd.byu.uapi+json`,
      `vnd.bzip3`,
      `vnd.c3voc.schedule+xml`,
      `vnd.cab-jscript`,
      `vnd.canon-cpdl`,
      `vnd.canon-lips`,
      `vnd.capasystems-pg+json`,
      `vnd.cel`,
      `vnd.cendio.thinlinc.clientconf`,
      `vnd.century-systems.tcp_stream`,
      `vnd.chemdraw+xml`,
      `vnd.chess-pgn`,
      `vnd.chipnuts.karaoke-mmd`,
      `vnd.ciedi`,
      `vnd.cinderella`,
      `vnd.cirpack.isdn-ext`,
      `vnd.citationstyles.style+xml`,
      `vnd.claymore`,
      `vnd.cloanto.rp9`,
      `vnd.clonk.c4group`,
      `vnd.cluetrust.cartomobile-config`,
      `vnd.cluetrust.cartomobile-config-pkg`,
      `vnd.cncf.helm.chart.content.v1.tar+gzip`,
      `vnd.cncf.helm.chart.provenance.v1.prov`,
      `vnd.cncf.helm.config.v1+json`,
      `vnd.coffeescript`,
      `vnd.collabio.xodocuments.document`,
      `vnd.collabio.xodocuments.document-template`,
      `vnd.collabio.xodocuments.presentation`,
      `vnd.collabio.xodocuments.presentation-template`,
      `vnd.collabio.xodocuments.spreadsheet`,
      `vnd.collabio.xodocuments.spreadsheet-template`,
      `vnd.collection+json`,
      `vnd.collection.doc+json`,
      `vnd.collection.next+json`,
      `vnd.comicbook+zip`,
      `vnd.comicbook-rar`,
      `vnd.commerce-battelle`,
      `vnd.commonspace`,
      `vnd.contact.cmsg`,
      `vnd.coreos.ignition+json`,
      `vnd.cosmocaller`,
      `vnd.crick.clicker`,
      `vnd.crick.clicker.keyboard`,
      `vnd.crick.clicker.palette`,
      `vnd.crick.clicker.template`,
      `vnd.crick.clicker.wordbank`,
      `vnd.criticaltools.wbs+xml`,
      `vnd.cryptii.pipe+json`,
      `vnd.crypto-shade-file`,
      `vnd.cryptomator.encrypted`,
      `vnd.cryptomator.vault`,
      `vnd.ctc-posml`,
      `vnd.ctct.ws+xml`,
      `vnd.cups-pdf`,
      `vnd.cups-postscript`,
      `vnd.cups-ppd`,
      `vnd.cups-raster`,
      `vnd.cups-raw`,
      `vnd.curl`,
      `vnd.curl.car`,
      `vnd.curl.pcurl`,
      `vnd.cyan.dean.root+xml`,
      `vnd.cybank`,
      `vnd.cyclonedx+json`,
      `vnd.cyclonedx+xml`,
      `vnd.d2l.coursepackage1p0+zip`,
      `vnd.d3m-dataset`,
      `vnd.d3m-problem`,
      `vnd.dart`,
      `vnd.data-vision.rdz`,
      `vnd.datalog`,
      `vnd.datapackage+json`,
      `vnd.dataresource+json`,
      `vnd.dbf`,
      `vnd.dcmp+xml`,
      `vnd.debian.binary-package`,
      `vnd.dece.data`,
      `vnd.dece.ttml+xml`,
      `vnd.dece.unspecified`,
      `vnd.dece.zip`,
      `vnd.denovo.fcselayout-link`,
      `vnd.desmume.movie`,
      `vnd.dir-bi.plate-dl-nosuffix`,
      `vnd.dm.delegation+xml`,
      `vnd.dna`,
      `vnd.document+json`,
      `vnd.dolby.mlp`,
      `vnd.dolby.mobile.1`,
      `vnd.dolby.mobile.2`,
      `vnd.doremir.scorecloud-binary-document`,
      `vnd.dpgraph`,
      `vnd.dreamfactory`,
      `vnd.drive+json`,
      `vnd.ds-keypoint`,
      `vnd.dtg.local`,
      `vnd.dtg.local.flash`,
      `vnd.dtg.local.html`,
      `vnd.dvb.ait`,
      `vnd.dvb.dvbisl+xml`,
      `vnd.dvb.dvbj`,
      `vnd.dvb.esgcontainer`,
      `vnd.dvb.ipdcdftnotifaccess`,
      `vnd.dvb.ipdcesgaccess`,
      `vnd.dvb.ipdcesgaccess2`,
      `vnd.dvb.ipdcesgpdd`,
      `vnd.dvb.ipdcroaming`,
      `vnd.dvb.iptv.alfec-base`,
      `vnd.dvb.iptv.alfec-enhancement`,
      `vnd.dvb.notif-aggregate-root+xml`,
      `vnd.dvb.notif-container+xml`,
      `vnd.dvb.notif-generic+xml`,
      `vnd.dvb.notif-ia-msglist+xml`,
      `vnd.dvb.notif-ia-registration-request+xml`,
      `vnd.dvb.notif-ia-registration-response+xml`,
      `vnd.dvb.notif-init+xml`,
      `vnd.dvb.pfr`,
      `vnd.dvb.service`,
      `vnd.dxr`,
      `vnd.dynageo`,
      `vnd.dzr`,
      `vnd.easykaraoke.cdgdownload`,
      `vnd.ecdis-update`,
      `vnd.ecip.rlp`,
      `vnd.eclipse.ditto+json`,
      `vnd.ecowin.chart`,
      `vnd.ecowin.filerequest`,
      `vnd.ecowin.fileupdate`,
      `vnd.ecowin.series`,
      `vnd.ecowin.seriesrequest`,
      `vnd.ecowin.seriesupdate`,
      `vnd.efi.img`,
      `vnd.efi.iso`,
      `vnd.eln+zip`,
      `vnd.emclient.accessrequest+xml`,
      `vnd.enliven`,
      `vnd.enphase.envoy`,
      `vnd.eprints.data+xml`,
      `vnd.epson.esf`,
      `vnd.epson.msf`,
      `vnd.epson.quickanime`,
      `vnd.epson.salt`,
      `vnd.epson.ssf`,
      `vnd.ericsson.quickcall`,
      `vnd.erofs`,
      `vnd.espass-espass+zip`,
      `vnd.eszigno3+xml`,
      `vnd.etsi.aoc+xml`,
      `vnd.etsi.asic-e+zip`,
      `vnd.etsi.asic-s+zip`,
      `vnd.etsi.cug+xml`,
      `vnd.etsi.iptvcommand+xml`,
      `vnd.etsi.iptvdiscovery+xml`,
      `vnd.etsi.iptvprofile+xml`,
      `vnd.etsi.iptvsad-bc+xml`,
      `vnd.etsi.iptvsad-cod+xml`,
      `vnd.etsi.iptvsad-npvr+xml`,
      `vnd.etsi.iptvservice+xml`,
      `vnd.etsi.iptvsync+xml`,
      `vnd.etsi.iptvueprofile+xml`,
      `vnd.etsi.mcid+xml`,
      `vnd.etsi.mheg5`,
      `vnd.etsi.overload-control-policy-dataset+xml`,
      `vnd.etsi.pstn+xml`,
      `vnd.etsi.sci+xml`,
      `vnd.etsi.simservs+xml`,
      `vnd.etsi.timestamp-token`,
      `vnd.etsi.tsl+xml`,
      `vnd.etsi.tsl.der`,
      `vnd.eu.kasparian.car+json`,
      `vnd.eudora.data`,
      `vnd.evolv.ecig.profile`,
      `vnd.evolv.ecig.settings`,
      `vnd.evolv.ecig.theme`,
      `vnd.exstream-empower+zip`,
      `vnd.exstream-package`,
      `vnd.ezpix-album`,
      `vnd.ezpix-package`,
      `vnd.f-secure.mobile`,
      `vnd.faf+yaml`,
      `vnd.familysearch.gedcom+zip`,
      `vnd.fastcopy-disk-image`,
      `vnd.fdf`,
      `vnd.fdsn.mseed`,
      `vnd.fdsn.seed`,
      `vnd.fdsn.stationxml+xml`,
      `vnd.ffsns`,
      `vnd.fgb`,
      `vnd.ficlab.flb+zip`,
      `vnd.filmit.zfc`,
      `vnd.fints`,
      `vnd.firemonkeys.cloudcell`,
      `vnd.flographit`,
      `vnd.fluxtime.clip`,
      `vnd.font-fontforge-sfd`,
      `vnd.framemaker`,
      `vnd.freelog.comic`,
      `vnd.frogans.fnc`,
      `vnd.frogans.ltf`,
      `vnd.fsc.weblaunch`,
      `vnd.fujifilm.fb.docuworks`,
      `vnd.fujifilm.fb.docuworks.binder`,
      `vnd.fujifilm.fb.docuworks.container`,
      `vnd.fujifilm.fb.jfi+xml`,
      `vnd.fujitsu.oasys`,
      `vnd.fujitsu.oasys2`,
      `vnd.fujitsu.oasys3`,
      `vnd.fujitsu.oasysgp`,
      `vnd.fujitsu.oasysprs`,
      `vnd.fujixerox.art-ex`,
      `vnd.fujixerox.art4`,
      `vnd.fujixerox.ddd`,
      `vnd.fujixerox.docuworks`,
      `vnd.fujixerox.docuworks.binder`,
      `vnd.fujixerox.docuworks.container`,
      `vnd.fujixerox.hbpl`,
      `vnd.fut-misnet`,
      `vnd.futoin+cbor`,
      `vnd.futoin+json`,
      `vnd.fuzzysheet`,
      `vnd.g3pix.g3fc`,
      `vnd.ga4gh.passport+jwt`,
      `vnd.genomatix.tuxedo`,
      `vnd.genozip`,
      `vnd.gentics.grd+json`,
      `vnd.gentoo.catmetadata+xml`,
      `vnd.gentoo.ebuild`,
      `vnd.gentoo.eclass`,
      `vnd.gentoo.gpkg`,
      `vnd.gentoo.manifest`,
      `vnd.gentoo.pkgmetadata+xml`,
      `vnd.gentoo.xpak`,
      `vnd.geo+json`,
      `vnd.geocube+xml`,
      `vnd.geogebra.file`,
      `vnd.geogebra.pinboard`,
      `vnd.geogebra.slides`,
      `vnd.geogebra.tool`,
      `vnd.geometry-explorer`,
      `vnd.geonext`,
      `vnd.geoplan`,
      `vnd.geospace`,
      `vnd.gerber`,
      `vnd.globalplatform.card-content-mgt`,
      `vnd.globalplatform.card-content-mgt-response`,
      `vnd.gmx`,
      `vnd.gnu.taler.exchange+json`,
      `vnd.gnu.taler.merchant+json`,
      `vnd.google-apps.audio`,
      `vnd.google-apps.document`,
      `vnd.google-apps.drawing`,
      `vnd.google-apps.drive-sdk`,
      `vnd.google-apps.file`,
      `vnd.google-apps.folder`,
      `vnd.google-apps.form`,
      `vnd.google-apps.fusiontable`,
      `vnd.google-apps.jam`,
      `vnd.google-apps.mail-layout`,
      `vnd.google-apps.map`,
      `vnd.google-apps.photo`,
      `vnd.google-apps.presentation`,
      `vnd.google-apps.script`,
      `vnd.google-apps.shortcut`,
      `vnd.google-apps.site`,
      `vnd.google-apps.spreadsheet`,
      `vnd.google-apps.unknown`,
      `vnd.google-apps.video`,
      `vnd.google-earth.kml+xml`,
      `vnd.google-earth.kmz`,
      `vnd.gov.sk.e-form+xml`,
      `vnd.gov.sk.e-form+zip`,
      `vnd.gov.sk.xmldatacontainer+xml`,
      `vnd.gpxsee.map+xml`,
      `vnd.grafeq`,
      `vnd.gridmp`,
      `vnd.groove-account`,
      `vnd.groove-help`,
      `vnd.groove-identity-message`,
      `vnd.groove-injector`,
      `vnd.groove-tool-message`,
      `vnd.groove-tool-template`,
      `vnd.groove-vcard`,
      `vnd.hal+json`,
      `vnd.hal+xml`,
      `vnd.handheld-entertainment+xml`,
      `vnd.hbci`,
      `vnd.hc+json`,
      `vnd.hcl-bireports`,
      `vnd.hdt`,
      `vnd.heroku+json`,
      `vnd.hhe.lesson-player`,
      `vnd.hp-hpgl`,
      `vnd.hp-hpid`,
      `vnd.hp-hps`,
      `vnd.hp-jlyt`,
      `vnd.hp-pcl`,
      `vnd.hp-pclxl`,
      `vnd.hsl`,
      `vnd.httphone`,
      `vnd.hydrostatix.sof-data`,
      `vnd.hyper+json`,
      `vnd.hyper-item+json`,
      `vnd.hyperdrive+json`,
      `vnd.hzn-3d-crossword`,
      `vnd.ibm.afplinedata`,
      `vnd.ibm.electronic-media`,
      `vnd.ibm.minipay`,
      `vnd.ibm.modcap`,
      `vnd.ibm.rights-management`,
      `vnd.ibm.secure-container`,
      `vnd.iccprofile`,
      `vnd.ieee.1905`,
      `vnd.igloader`,
      `vnd.imagemeter.folder+zip`,
      `vnd.imagemeter.image+zip`,
      `vnd.immervision-ivp`,
      `vnd.immervision-ivu`,
      `vnd.ims.imsccv1p1`,
      `vnd.ims.imsccv1p2`,
      `vnd.ims.imsccv1p3`,
      `vnd.ims.lis.v2.result+json`,
      `vnd.ims.lti.v2.toolconsumerprofile+json`,
      `vnd.ims.lti.v2.toolproxy+json`,
      `vnd.ims.lti.v2.toolproxy.id+json`,
      `vnd.ims.lti.v2.toolsettings+json`,
      `vnd.ims.lti.v2.toolsettings.simple+json`,
      `vnd.informedcontrol.rms+xml`,
      `vnd.informix-visionary`,
      `vnd.infotech.project`,
      `vnd.infotech.project+xml`,
      `vnd.innopath.wamp.notification`,
      `vnd.insors.igm`,
      `vnd.intercon.formnet`,
      `vnd.intergeo`,
      `vnd.intertrust.digibox`,
      `vnd.intertrust.nncp`,
      `vnd.intu.qbo`,
      `vnd.intu.qfx`,
      `vnd.ipfs.ipns-record`,
      `vnd.ipld.car`,
      `vnd.ipld.dag-cbor`,
      `vnd.ipld.dag-json`,
      `vnd.ipld.raw`,
      `vnd.iptc.g2.catalogitem+xml`,
      `vnd.iptc.g2.conceptitem+xml`,
      `vnd.iptc.g2.knowledgeitem+xml`,
      `vnd.iptc.g2.newsitem+xml`,
      `vnd.iptc.g2.newsmessage+xml`,
      `vnd.iptc.g2.packageitem+xml`,
      `vnd.iptc.g2.planningitem+xml`,
      `vnd.ipunplugged.rcprofile`,
      `vnd.irepository.package+xml`,
      `vnd.is-xpr`,
      `vnd.isac.fcs`,
      `vnd.iso11783-10+zip`,
      `vnd.jam`,
      `vnd.japannet-directory-service`,
      `vnd.japannet-jpnstore-wakeup`,
      `vnd.japannet-payment-wakeup`,
      `vnd.japannet-registration`,
      `vnd.japannet-registration-wakeup`,
      `vnd.japannet-setstore-wakeup`,
      `vnd.japannet-verification`,
      `vnd.japannet-verification-wakeup`,
      `vnd.jcp.javame.midlet-rms`,
      `vnd.jisp`,
      `vnd.joost.joda-archive`,
      `vnd.jsk.isdn-ngn`,
      `vnd.kahootz`,
      `vnd.kde.karbon`,
      `vnd.kde.kchart`,
      `vnd.kde.kformula`,
      `vnd.kde.kivio`,
      `vnd.kde.kontour`,
      `vnd.kde.kpresenter`,
      `vnd.kde.kspread`,
      `vnd.kde.kword`,
      `vnd.kdl`,
      `vnd.kenameaapp`,
      `vnd.keyman.kmp+zip`,
      `vnd.keyman.kmx`,
      `vnd.kidspiration`,
      `vnd.kinar`,
      `vnd.koan`,
      `vnd.kodak-descriptor`,
      `vnd.las`,
      `vnd.las.las+json`,
      `vnd.las.las+xml`,
      `vnd.laszip`,
      `vnd.ldev.productlicensing`,
      `vnd.leap+json`,
      `vnd.liberty-request+xml`,
      `vnd.llamagraphics.life-balance.desktop`,
      `vnd.llamagraphics.life-balance.exchange+xml`,
      `vnd.logipipe.circuit+zip`,
      `vnd.loom`,
      `vnd.lotus-1-2-3`,
      `vnd.lotus-approach`,
      `vnd.lotus-freelance`,
      `vnd.lotus-notes`,
      `vnd.lotus-organizer`,
      `vnd.lotus-screencam`,
      `vnd.lotus-wordpro`,
      `vnd.macports.portpkg`,
      `vnd.maml`,
      `vnd.mapbox-vector-tile`,
      `vnd.marlin.drm.actiontoken+xml`,
      `vnd.marlin.drm.conftoken+xml`,
      `vnd.marlin.drm.license+xml`,
      `vnd.marlin.drm.mdcf`,
      `vnd.mason+json`,
      `vnd.maxar.archive.3tz+zip`,
      `vnd.maxmind.maxmind-db`,
      `vnd.mcd`,
      `vnd.mdl`,
      `vnd.mdl-mbsdf`,
      `vnd.medcalcdata`,
      `vnd.mediastation.cdkey`,
      `vnd.medicalholodeck.recordxr`,
      `vnd.meridian-slingshot`,
      `vnd.mermaid`,
      `vnd.mfer`,
      `vnd.mfmp`,
      `vnd.micro+json`,
      `vnd.micrografx.flo`,
      `vnd.micrografx.igx`,
      `vnd.microsoft.portable-executable`,
      `vnd.microsoft.windows.thumbnail-cache`,
      `vnd.miele+json`,
      `vnd.mif`,
      `vnd.minisoft-hp3000-save`,
      `vnd.mitsubishi.misty-guard.trustweb`,
      `vnd.mobius.daf`,
      `vnd.mobius.dis`,
      `vnd.mobius.mbk`,
      `vnd.mobius.mqy`,
      `vnd.mobius.msl`,
      `vnd.mobius.plc`,
      `vnd.mobius.txf`,
      `vnd.modl`,
      `vnd.mophun.application`,
      `vnd.mophun.certificate`,
      `vnd.motorola.flexsuite`,
      `vnd.motorola.flexsuite.adsi`,
      `vnd.motorola.flexsuite.fis`,
      `vnd.motorola.flexsuite.gotap`,
      `vnd.motorola.flexsuite.kmr`,
      `vnd.motorola.flexsuite.ttc`,
      `vnd.motorola.flexsuite.wem`,
      `vnd.motorola.iprm`,
      `vnd.mozilla.xul+xml`,
      `vnd.ms-3mfdocument`,
      `vnd.ms-artgalry`,
      `vnd.ms-asf`,
      `vnd.ms-cab-compressed`,
      `vnd.ms-color.iccprofile`,
      `vnd.ms-excel`,
      `vnd.ms-excel.addin.macroenabled.12`,
      `vnd.ms-excel.sheet.binary.macroenabled.12`,
      `vnd.ms-excel.sheet.macroenabled.12`,
      `vnd.ms-excel.template.macroenabled.12`,
      `vnd.ms-fontobject`,
      `vnd.ms-htmlhelp`,
      `vnd.ms-ims`,
      `vnd.ms-lrm`,
      `vnd.ms-office.activex+xml`,
      `vnd.ms-officetheme`,
      `vnd.ms-opentype`,
      `vnd.ms-outlook`,
      `vnd.ms-package.obfuscated-opentype`,
      `vnd.ms-pki.seccat`,
      `vnd.ms-pki.stl`,
      `vnd.ms-playready.initiator+xml`,
      `vnd.ms-powerpoint`,
      `vnd.ms-powerpoint.addin.macroenabled.12`,
      `vnd.ms-powerpoint.presentation.macroenabled.12`,
      `vnd.ms-powerpoint.slide.macroenabled.12`,
      `vnd.ms-powerpoint.slideshow.macroenabled.12`,
      `vnd.ms-powerpoint.template.macroenabled.12`,
      `vnd.ms-printdevicecapabilities+xml`,
      `vnd.ms-printing.printticket+xml`,
      `vnd.ms-printschematicket+xml`,
      `vnd.ms-project`,
      `vnd.ms-tnef`,
      `vnd.ms-visio.viewer`,
      `vnd.ms-windows.devicepairing`,
      `vnd.ms-windows.nwprinting.oob`,
      `vnd.ms-windows.printerpairing`,
      `vnd.ms-windows.wsd.oob`,
      `vnd.ms-wmdrm.lic-chlg-req`,
      `vnd.ms-wmdrm.lic-resp`,
      `vnd.ms-wmdrm.meter-chlg-req`,
      `vnd.ms-wmdrm.meter-resp`,
      `vnd.ms-word.document.macroenabled.12`,
      `vnd.ms-word.template.macroenabled.12`,
      `vnd.ms-works`,
      `vnd.ms-wpl`,
      `vnd.ms-xpsdocument`,
      `vnd.msa-disk-image`,
      `vnd.mseq`,
      `vnd.msgpack`,
      `vnd.msign`,
      `vnd.multiad.creator`,
      `vnd.multiad.creator.cif`,
      `vnd.music-niff`,
      `vnd.musician`,
      `vnd.muvee.style`,
      `vnd.mynfc`,
      `vnd.nacamar.ybrid+json`,
      `vnd.nato.bindingdataobject+cbor`,
      `vnd.nato.bindingdataobject+json`,
      `vnd.nato.bindingdataobject+xml`,
      `vnd.nato.openxmlformats-package.iepd+zip`,
      `vnd.ncd.control`,
      `vnd.ncd.reference`,
      `vnd.nearst.inv+json`,
      `vnd.nebumind.line`,
      `vnd.nervana`,
      `vnd.netfpx`,
      `vnd.neurolanguage.nlu`,
      `vnd.nimn`,
      `vnd.nintendo.nitro.rom`,
      `vnd.nintendo.snes.rom`,
      `vnd.nitf`,
      `vnd.noblenet-directory`,
      `vnd.noblenet-sealer`,
      `vnd.noblenet-web`,
      `vnd.nokia.catalogs`,
      `vnd.nokia.conml+wbxml`,
      `vnd.nokia.conml+xml`,
      `vnd.nokia.iptv.config+xml`,
      `vnd.nokia.isds-radio-presets`,
      `vnd.nokia.landmark+wbxml`,
      `vnd.nokia.landmark+xml`,
      `vnd.nokia.landmarkcollection+xml`,
      `vnd.nokia.n-gage.ac+xml`,
      `vnd.nokia.n-gage.data`,
      `vnd.nokia.n-gage.symbian.install`,
      `vnd.nokia.ncd`,
      `vnd.nokia.pcd+wbxml`,
      `vnd.nokia.pcd+xml`,
      `vnd.nokia.radio-preset`,
      `vnd.nokia.radio-presets`,
      `vnd.novadigm.edm`,
      `vnd.novadigm.edx`,
      `vnd.novadigm.ext`,
      `vnd.ntt-local.content-share`,
      `vnd.ntt-local.file-transfer`,
      `vnd.ntt-local.ogw_remote-access`,
      `vnd.ntt-local.sip-ta_remote`,
      `vnd.ntt-local.sip-ta_tcp_stream`,
      `vnd.nubaltec.nudoku-game`,
      `vnd.oai.workflows`,
      `vnd.oai.workflows+json`,
      `vnd.oai.workflows+yaml`,
      `vnd.oasis.opendocument.base`,
      `vnd.oasis.opendocument.chart`,
      `vnd.oasis.opendocument.chart-template`,
      `vnd.oasis.opendocument.database`,
      `vnd.oasis.opendocument.formula`,
      `vnd.oasis.opendocument.formula-template`,
      `vnd.oasis.opendocument.graphics`,
      `vnd.oasis.opendocument.graphics-template`,
      `vnd.oasis.opendocument.image`,
      `vnd.oasis.opendocument.image-template`,
      `vnd.oasis.opendocument.presentation`,
      `vnd.oasis.opendocument.presentation-template`,
      `vnd.oasis.opendocument.spreadsheet`,
      `vnd.oasis.opendocument.spreadsheet-template`,
      `vnd.oasis.opendocument.text`,
      `vnd.oasis.opendocument.text-master`,
      `vnd.oasis.opendocument.text-master-template`,
      `vnd.oasis.opendocument.text-template`,
      `vnd.oasis.opendocument.text-web`,
      `vnd.obn`,
      `vnd.ocf+cbor`,
      `vnd.oci.image.manifest.v1+json`,
      `vnd.oftn.l10n+json`,
      `vnd.oipf.contentaccessdownload+xml`,
      `vnd.oipf.contentaccessstreaming+xml`,
      `vnd.oipf.cspg-hexbinary`,
      `vnd.oipf.dae.svg+xml`,
      `vnd.oipf.dae.xhtml+xml`,
      `vnd.oipf.mippvcontrolmessage+xml`,
      `vnd.oipf.pae.gem`,
      `vnd.oipf.spdiscovery+xml`,
      `vnd.oipf.spdlist+xml`,
      `vnd.oipf.ueprofile+xml`,
      `vnd.oipf.userprofile+xml`,
      `vnd.olpc-sugar`,
      `vnd.oma-scws-config`,
      `vnd.oma-scws-http-request`,
      `vnd.oma-scws-http-response`,
      `vnd.oma.bcast.associated-procedure-parameter+xml`,
      `vnd.oma.bcast.drm-trigger+xml`,
      `vnd.oma.bcast.imd+xml`,
      `vnd.oma.bcast.ltkm`,
      `vnd.oma.bcast.notification+xml`,
      `vnd.oma.bcast.provisioningtrigger`,
      `vnd.oma.bcast.sgboot`,
      `vnd.oma.bcast.sgdd+xml`,
      `vnd.oma.bcast.sgdu`,
      `vnd.oma.bcast.simple-symbol-container`,
      `vnd.oma.bcast.smartcard-trigger+xml`,
      `vnd.oma.bcast.sprov+xml`,
      `vnd.oma.bcast.stkm`,
      `vnd.oma.cab-address-book+xml`,
      `vnd.oma.cab-feature-handler+xml`,
      `vnd.oma.cab-pcc+xml`,
      `vnd.oma.cab-subs-invite+xml`,
      `vnd.oma.cab-user-prefs+xml`,
      `vnd.oma.dcd`,
      `vnd.oma.dcdc`,
      `vnd.oma.dd2+xml`,
      `vnd.oma.drm.risd+xml`,
      `vnd.oma.group-usage-list+xml`,
      `vnd.oma.lwm2m+cbor`,
      `vnd.oma.lwm2m+json`,
      `vnd.oma.lwm2m+tlv`,
      `vnd.oma.pal+xml`,
      `vnd.oma.poc.detailed-progress-report+xml`,
      `vnd.oma.poc.final-report+xml`,
      `vnd.oma.poc.groups+xml`,
      `vnd.oma.poc.invocation-descriptor+xml`,
      `vnd.oma.poc.optimized-progress-report+xml`,
      `vnd.oma.push`,
      `vnd.oma.scidm.messages+xml`,
      `vnd.oma.xcap-directory+xml`,
      `vnd.omads-email+xml`,
      `vnd.omads-file+xml`,
      `vnd.omads-folder+xml`,
      `vnd.omaloc-supl-init`,
      `vnd.oms.cellular-cose-content+cbor`,
      `vnd.onepager`,
      `vnd.onepagertamp`,
      `vnd.onepagertamx`,
      `vnd.onepagertat`,
      `vnd.onepagertatp`,
      `vnd.onepagertatx`,
      `vnd.onvif.metadata`,
      `vnd.openblox.game+xml`,
      `vnd.openblox.game-binary`,
      `vnd.openeye.oeb`,
      `vnd.openofficeorg.extension`,
      `vnd.openprinttag`,
      `vnd.openstreetmap.data+xml`,
      `vnd.opentimestamps.ots`,
      `vnd.openvpi.dspx+json`,
      `vnd.openxmlformats-officedocument.custom-properties+xml`,
      `vnd.openxmlformats-officedocument.customxmlproperties+xml`,
      `vnd.openxmlformats-officedocument.drawing+xml`,
      `vnd.openxmlformats-officedocument.drawingml.chart+xml`,
      `vnd.openxmlformats-officedocument.drawingml.chartshapes+xml`,
      `vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml`,
      `vnd.openxmlformats-officedocument.drawingml.diagramdata+xml`,
      `vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml`,
      `vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml`,
      `vnd.openxmlformats-officedocument.extended-properties+xml`,
      `vnd.openxmlformats-officedocument.presentationml.commentauthors+xml`,
      `vnd.openxmlformats-officedocument.presentationml.comments+xml`,
      `vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml`,
      `vnd.openxmlformats-officedocument.presentationml.notesmaster+xml`,
      `vnd.openxmlformats-officedocument.presentationml.notesslide+xml`,
      `vnd.openxmlformats-officedocument.presentationml.presentation`,
      `vnd.openxmlformats-officedocument.presentationml.presentation.main+xml`,
      `vnd.openxmlformats-officedocument.presentationml.presprops+xml`,
      `vnd.openxmlformats-officedocument.presentationml.slide`,
      `vnd.openxmlformats-officedocument.presentationml.slide+xml`,
      `vnd.openxmlformats-officedocument.presentationml.slidelayout+xml`,
      `vnd.openxmlformats-officedocument.presentationml.slidemaster+xml`,
      `vnd.openxmlformats-officedocument.presentationml.slideshow`,
      `vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml`,
      `vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml`,
      `vnd.openxmlformats-officedocument.presentationml.tablestyles+xml`,
      `vnd.openxmlformats-officedocument.presentationml.tags+xml`,
      `vnd.openxmlformats-officedocument.presentationml.template`,
      `vnd.openxmlformats-officedocument.presentationml.template.main+xml`,
      `vnd.openxmlformats-officedocument.presentationml.viewprops+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.comments+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.connections+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
      `vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.styles+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.table+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.template`,
      `vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml`,
      `vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml`,
      `vnd.openxmlformats-officedocument.theme+xml`,
      `vnd.openxmlformats-officedocument.themeoverride+xml`,
      `vnd.openxmlformats-officedocument.vmldrawing`,
      `vnd.openxmlformats-officedocument.wordprocessingml.comments+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.document`,
      `vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.footer+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.settings+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.styles+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.template`,
      `vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml`,
      `vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml`,
      `vnd.openxmlformats-package.core-properties+xml`,
      `vnd.openxmlformats-package.digital-signature-xmlsignature+xml`,
      `vnd.openxmlformats-package.relationships+xml`,
      `vnd.oracle.resource+json`,
      `vnd.orange.indata`,
      `vnd.osa.netdeploy`,
      `vnd.osgeo.mapguide.package`,
      `vnd.osgi.bundle`,
      `vnd.osgi.dp`,
      `vnd.osgi.subsystem`,
      `vnd.otps.ct-kip+xml`,
      `vnd.oxli.countgraph`,
      `vnd.pagerduty+json`,
      `vnd.palm`,
      `vnd.panoply`,
      `vnd.paos.xml`,
      `vnd.patentdive`,
      `vnd.patientecommsdoc`,
      `vnd.pawaafile`,
      `vnd.pcos`,
      `vnd.pg.format`,
      `vnd.pg.osasli`,
      `vnd.piaccess.application-licence`,
      `vnd.picsel`,
      `vnd.pmi.widget`,
      `vnd.pmtiles`,
      `vnd.poc.group-advertisement+xml`,
      `vnd.pocketlearn`,
      `vnd.powerbuilder6`,
      `vnd.powerbuilder6-s`,
      `vnd.powerbuilder7`,
      `vnd.powerbuilder7-s`,
      `vnd.powerbuilder75`,
      `vnd.powerbuilder75-s`,
      `vnd.pp.systemverify+xml`,
      `vnd.preminet`,
      `vnd.previewsystems.box`,
      `vnd.procreate.brush`,
      `vnd.procreate.brushset`,
      `vnd.procreate.dream`,
      `vnd.project-graph`,
      `vnd.proteus.magazine`,
      `vnd.psfs`,
      `vnd.pt.mundusmundi`,
      `vnd.publishare-delta-tree`,
      `vnd.pvi.ptid1`,
      `vnd.pwg-multiplexed`,
      `vnd.pwg-xhtml-print+xml`,
      `vnd.pyon+json`,
      `vnd.qualcomm.brew-app-res`,
      `vnd.quarantainenet`,
      `vnd.quark.quarkxpress`,
      `vnd.quobject-quoxdocument`,
      `vnd.r74n.sandboxels+json`,
      `vnd.radisys.moml+xml`,
      `vnd.radisys.msml+xml`,
      `vnd.radisys.msml-audit+xml`,
      `vnd.radisys.msml-audit-conf+xml`,
      `vnd.radisys.msml-audit-conn+xml`,
      `vnd.radisys.msml-audit-dialog+xml`,
      `vnd.radisys.msml-audit-stream+xml`,
      `vnd.radisys.msml-conf+xml`,
      `vnd.radisys.msml-dialog+xml`,
      `vnd.radisys.msml-dialog-base+xml`,
      `vnd.radisys.msml-dialog-fax-detect+xml`,
      `vnd.radisys.msml-dialog-fax-sendrecv+xml`,
      `vnd.radisys.msml-dialog-group+xml`,
      `vnd.radisys.msml-dialog-speech+xml`,
      `vnd.radisys.msml-dialog-transform+xml`,
      `vnd.rainstor.data`,
      `vnd.rapid`,
      `vnd.rar`,
      `vnd.realvnc.bed`,
      `vnd.recordare.musicxml`,
      `vnd.recordare.musicxml+xml`,
      `vnd.relpipe`,
      `vnd.renlearn.rlprint`,
      `vnd.resilient.logic`,
      `vnd.restful+json`,
      `vnd.rig.cryptonote`,
      `vnd.rim.cod`,
      `vnd.rn-realmedia`,
      `vnd.rn-realmedia-vbr`,
      `vnd.route66.link66+xml`,
      `vnd.rs-274x`,
      `vnd.ruckus.download`,
      `vnd.s3sms`,
      `vnd.sailingtracker.track`,
      `vnd.sar`,
      `vnd.sbm.cid`,
      `vnd.sbm.mid2`,
      `vnd.scribus`,
      `vnd.sealed.3df`,
      `vnd.sealed.csf`,
      `vnd.sealed.doc`,
      `vnd.sealed.eml`,
      `vnd.sealed.mht`,
      `vnd.sealed.net`,
      `vnd.sealed.ppt`,
      `vnd.sealed.tiff`,
      `vnd.sealed.xls`,
      `vnd.sealedmedia.softseal.html`,
      `vnd.sealedmedia.softseal.pdf`,
      `vnd.seemail`,
      `vnd.seis+json`,
      `vnd.sema`,
      `vnd.semd`,
      `vnd.semf`,
      `vnd.shade-save-file`,
      `vnd.shana.informed.formdata`,
      `vnd.shana.informed.formtemplate`,
      `vnd.shana.informed.interchange`,
      `vnd.shana.informed.package`,
      `vnd.shootproof+json`,
      `vnd.shopkick+json`,
      `vnd.shp`,
      `vnd.shx`,
      `vnd.sigrok.session`,
      `vnd.simtech-mindmapper`,
      `vnd.siren+json`,
      `vnd.sirtx.vmv0`,
      `vnd.sketchometry`,
      `vnd.smaf`,
      `vnd.smart.notebook`,
      `vnd.smart.teacher`,
      `vnd.smintio.portals.archive`,
      `vnd.snesdev-page-table`,
      `vnd.software602.filler.form+xml`,
      `vnd.software602.filler.form-xml-zip`,
      `vnd.solent.sdkm+xml`,
      `vnd.spotfire.dxp`,
      `vnd.spotfire.sfs`,
      `vnd.sqlite3`,
      `vnd.sss-cod`,
      `vnd.sss-dtf`,
      `vnd.sss-ntf`,
      `vnd.stardivision.calc`,
      `vnd.stardivision.draw`,
      `vnd.stardivision.impress`,
      `vnd.stardivision.math`,
      `vnd.stardivision.writer`,
      `vnd.stardivision.writer-global`,
      `vnd.stepmania.package`,
      `vnd.stepmania.stepchart`,
      `vnd.street-stream`,
      `vnd.sun.wadl+xml`,
      `vnd.sun.xml.calc`,
      `vnd.sun.xml.calc.template`,
      `vnd.sun.xml.draw`,
      `vnd.sun.xml.draw.template`,
      `vnd.sun.xml.impress`,
      `vnd.sun.xml.impress.template`,
      `vnd.sun.xml.math`,
      `vnd.sun.xml.writer`,
      `vnd.sun.xml.writer.global`,
      `vnd.sun.xml.writer.template`,
      `vnd.superfile.super`,
      `vnd.sus-calendar`,
      `vnd.svd`,
      `vnd.swiftview-ics`,
      `vnd.sybyl.mol2`,
      `vnd.sycle+xml`,
      `vnd.syft+json`,
      `vnd.symbian.install`,
      `vnd.syncml+xml`,
      `vnd.syncml.dm+wbxml`,
      `vnd.syncml.dm+xml`,
      `vnd.syncml.dm.notification`,
      `vnd.syncml.dmddf+wbxml`,
      `vnd.syncml.dmddf+xml`,
      `vnd.syncml.dmtnds+wbxml`,
      `vnd.syncml.dmtnds+xml`,
      `vnd.syncml.ds.notification`,
      `vnd.tableschema+json`,
      `vnd.tao.intent-module-archive`,
      `vnd.tcpdump.pcap`,
      `vnd.think-cell.ppttc+json`,
      `vnd.tmd.mediaflex.api+xml`,
      `vnd.tml`,
      `vnd.tmobile-livetv`,
      `vnd.tri.onesource`,
      `vnd.trid.tpt`,
      `vnd.triscape.mxs`,
      `vnd.trueapp`,
      `vnd.truedoc`,
      `vnd.ubisoft.webplayer`,
      `vnd.ufdl`,
      `vnd.uic.dosipas.v1`,
      `vnd.uic.dosipas.v2`,
      `vnd.uic.osdm+json`,
      `vnd.uic.tlb-fcb`,
      `vnd.uiq.theme`,
      `vnd.umajin`,
      `vnd.unity`,
      `vnd.uoml+xml`,
      `vnd.uplanet.alert`,
      `vnd.uplanet.alert-wbxml`,
      `vnd.uplanet.bearer-choice`,
      `vnd.uplanet.bearer-choice-wbxml`,
      `vnd.uplanet.cacheop`,
      `vnd.uplanet.cacheop-wbxml`,
      `vnd.uplanet.channel`,
      `vnd.uplanet.channel-wbxml`,
      `vnd.uplanet.list`,
      `vnd.uplanet.list-wbxml`,
      `vnd.uplanet.listcmd`,
      `vnd.uplanet.listcmd-wbxml`,
      `vnd.uplanet.signal`,
      `vnd.uri-map`,
      `vnd.valve.source.material`,
      `vnd.vcx`,
      `vnd.vd-study`,
      `vnd.vectorworks`,
      `vnd.vel+json`,
      `vnd.veraison.tsm-report+cbor`,
      `vnd.veraison.tsm-report+json`,
      `vnd.verifier-attestation+jwt`,
      `vnd.verimatrix.vcas`,
      `vnd.veritone.aion+json`,
      `vnd.veryant.thin`,
      `vnd.ves.encrypted`,
      `vnd.vidsoft.vidconference`,
      `vnd.visio`,
      `vnd.visionary`,
      `vnd.vividence.scriptfile`,
      `vnd.vocalshaper.vsp4`,
      `vnd.vsf`,
      `vnd.vuq`,
      `vnd.wantverse`,
      `vnd.wap.sic`,
      `vnd.wap.slc`,
      `vnd.wap.wbxml`,
      `vnd.wap.wmlc`,
      `vnd.wap.wmlscriptc`,
      `vnd.wasmflow.wafl`,
      `vnd.webturbo`,
      `vnd.wfa.dpp`,
      `vnd.wfa.p2p`,
      `vnd.wfa.wsc`,
      `vnd.windows.devicepairing`,
      `vnd.wmap`,
      `vnd.wmc`,
      `vnd.wmf.bootstrap`,
      `vnd.wolfram.mathematica`,
      `vnd.wolfram.mathematica.package`,
      `vnd.wolfram.player`,
      `vnd.wordlift`,
      `vnd.wordperfect`,
      `vnd.wqd`,
      `vnd.wrq-hp3000-labelled`,
      `vnd.wt.stf`,
      `vnd.wv.csp+wbxml`,
      `vnd.wv.csp+xml`,
      `vnd.wv.ssp+xml`,
      `vnd.xacml+json`,
      `vnd.xara`,
      `vnd.xarin.cpj`,
      `vnd.xcdn`,
      `vnd.xecrets-encrypted`,
      `vnd.xfdl`,
      `vnd.xfdl.webform`,
      `vnd.xmi+xml`,
      `vnd.xmpie.cpkg`,
      `vnd.xmpie.dpkg`,
      `vnd.xmpie.plan`,
      `vnd.xmpie.ppkg`,
      `vnd.xmpie.xlim`,
      `vnd.yamaha.hv-dic`,
      `vnd.yamaha.hv-script`,
      `vnd.yamaha.hv-voice`,
      `vnd.yamaha.openscoreformat`,
      `vnd.yamaha.openscoreformat.osfpvg+xml`,
      `vnd.yamaha.remote-setup`,
      `vnd.yamaha.smaf-audio`,
      `vnd.yamaha.smaf-phrase`,
      `vnd.yamaha.through-ngn`,
      `vnd.yamaha.tunnel-udpencap`,
      `vnd.yaoweme`,
      `vnd.yellowriver-custom-menu`,
      `vnd.zoho-presentation.show`,
      `vnd.zul`,
      `vnd.zzazz.deck+xml`,
      `voicexml+xml`,
      `voucher-cms+json`,
      `voucher-jws+json`,
      `vp`,
      `vp+cose`,
      `vp+jwt`,
      `vp+sd-jwt`,
      `vq-rtcpxr`,
      `wasm`,
      `watcherinfo+xml`,
      `webpush-options+json`,
      `whoispp-query`,
      `whoispp-response`,
      `widget`,
      `winhlp`,
      `wita`,
      `wordperfect5.1`,
      `wsdl+xml`,
      `wspolicy+xml`,
      `x-7z-compressed`,
      `x-abiword`,
      `x-ace-compressed`,
      `x-amf`,
      `x-apple-diskimage`,
      `x-arj`,
      `x-authorware-bin`,
      `x-authorware-map`,
      `x-authorware-seg`,
      `x-bcpio`,
      `x-bdoc`,
      `x-bittorrent`,
      `x-blender`,
      `x-blorb`,
      `x-bzip`,
      `x-bzip2`,
      `x-cbr`,
      `x-cdlink`,
      `x-cfs-compressed`,
      `x-chat`,
      `x-chess-pgn`,
      `x-chrome-extension`,
      `x-cocoa`,
      `x-compress`,
      `x-compressed`,
      `x-conference`,
      `x-cpio`,
      `x-csh`,
      `x-deb`,
      `x-debian-package`,
      `x-dgc-compressed`,
      `x-director`,
      `x-doom`,
      `x-dtbncx+xml`,
      `x-dtbook+xml`,
      `x-dtbresource+xml`,
      `x-dvi`,
      `x-envoy`,
      `x-eva`,
      `x-font-bdf`,
      `x-font-dos`,
      `x-font-framemaker`,
      `x-font-ghostscript`,
      `x-font-libgrx`,
      `x-font-linux-psf`,
      `x-font-pcf`,
      `x-font-snf`,
      `x-font-speedo`,
      `x-font-sunos-news`,
      `x-font-type1`,
      `x-font-vfont`,
      `x-freearc`,
      `x-futuresplash`,
      `x-gca-compressed`,
      `x-glulx`,
      `x-gnumeric`,
      `x-gramps-xml`,
      `x-gtar`,
      `x-gzip`,
      `x-hdf`,
      `x-httpd-php`,
      `x-install-instructions`,
      `x-ipynb+json`,
      `x-iso9660-image`,
      `x-iwork-keynote-sffkey`,
      `x-iwork-numbers-sffnumbers`,
      `x-iwork-pages-sffpages`,
      `x-java-archive-diff`,
      `x-java-jnlp-file`,
      `x-javascript`,
      `x-keepass2`,
      `x-latex`,
      `x-lua-bytecode`,
      `x-lzh-compressed`,
      `x-makeself`,
      `x-mie`,
      `x-mobipocket-ebook`,
      `x-mpegurl`,
      `x-ms-application`,
      `x-ms-shortcut`,
      `x-ms-wmd`,
      `x-ms-wmz`,
      `x-ms-xbap`,
      `x-msaccess`,
      `x-msbinder`,
      `x-mscardfile`,
      `x-msclip`,
      `x-msdos-program`,
      `x-msdownload`,
      `x-msmediaview`,
      `x-msmetafile`,
      `x-msmoney`,
      `x-mspublisher`,
      `x-msschedule`,
      `x-msterminal`,
      `x-mswrite`,
      `x-netcdf`,
      `x-ns-proxy-autoconfig`,
      `x-nzb`,
      `x-perl`,
      `x-pilot`,
      `x-pkcs12`,
      `x-pkcs7-certificates`,
      `x-pkcs7-certreqresp`,
      `x-pki-message`,
      `x-rar-compressed`,
      `x-redhat-package-manager`,
      `x-research-info-systems`,
      `x-sea`,
      `x-sh`,
      `x-shar`,
      `x-shockwave-flash`,
      `x-silverlight-app`,
      `x-sql`,
      `x-stuffit`,
      `x-stuffitx`,
      `x-subrip`,
      `x-sv4cpio`,
      `x-sv4crc`,
      `x-t3vm-image`,
      `x-tads`,
      `x-tar`,
      `x-tcl`,
      `x-tex`,
      `x-tex-tfm`,
      `x-texinfo`,
      `x-tgif`,
      `x-ustar`,
      `x-virtualbox-hdd`,
      `x-virtualbox-ova`,
      `x-virtualbox-ovf`,
      `x-virtualbox-vbox`,
      `x-virtualbox-vbox-extpack`,
      `x-virtualbox-vdi`,
      `x-virtualbox-vhd`,
      `x-virtualbox-vmdk`,
      `x-wais-source`,
      `x-web-app-manifest+json`,
      `x-www-form-urlencoded`,
      `x-x509-ca-cert`,
      `x-x509-ca-ra-cert`,
      `x-x509-next-ca-cert`,
      `x-xfig`,
      `x-xliff+xml`,
      `x-xpinstall`,
      `x-xz`,
      `x-zip-compressed`,
      `x-zmachine`,
      `x400-bp`,
      `xacml+xml`,
      `xaml+xml`,
      `xcap-att+xml`,
      `xcap-caps+xml`,
      `xcap-diff+xml`,
      `xcap-el+xml`,
      `xcap-error+xml`,
      `xcap-ns+xml`,
      `xcon-conference-info+xml`,
      `xcon-conference-info-diff+xml`,
      `xenc+xml`,
      `xfdf`,
      `xhtml+xml`,
      `xhtml-voice+xml`,
      `xliff+xml`,
      `xml`,
      `xml-dtd`,
      `xml-external-parsed-entity`,
      `xml-patch+xml`,
      `xmpp+xml`,
      `xop+xml`,
      `xproc+xml`,
      `xslt+xml`,
      `xspf+xml`,
      `xv+xml`,
      `yaml`,
      `yang`,
      `yang-data+cbor`,
      `yang-data+json`,
      `yang-data+xml`,
      `yang-patch+json`,
      `yang-patch+xml`,
      `yang-sid+json`,
      `yin+xml`,
      `zip`,
      `zip+dotlottie`,
      `zlib`,
      `zstd`
    )
  }

  object audio {
    lazy val `1d-interleaved-parityfec`: MediaType =
      MediaType("audio", "1d-interleaved-parityfec", compressible = false, binary = true)

    lazy val `32kadpcm`: MediaType =
      MediaType("audio", "32kadpcm", compressible = false, binary = true)

    lazy val `3gpp`: MediaType =
      MediaType("audio", "3gpp", compressible = false, binary = true, fileExtensions = List("3gpp"))

    lazy val `3gpp2`: MediaType =
      MediaType("audio", "3gpp2", compressible = false, binary = true)

    lazy val `aac`: MediaType =
      MediaType("audio", "aac", compressible = false, binary = true, fileExtensions = List("adts", "aac"))

    lazy val `ac3`: MediaType =
      MediaType("audio", "ac3", compressible = false, binary = true)

    lazy val `adpcm`: MediaType =
      MediaType("audio", "adpcm", compressible = false, binary = true, fileExtensions = List("adp"))

    lazy val `amr`: MediaType =
      MediaType("audio", "amr", compressible = false, binary = true, fileExtensions = List("amr"))

    lazy val `amr-wb`: MediaType =
      MediaType("audio", "amr-wb", compressible = false, binary = true)

    lazy val `amr-wb+`: MediaType =
      MediaType("audio", "amr-wb+", compressible = false, binary = true)

    lazy val `aptx`: MediaType =
      MediaType("audio", "aptx", compressible = false, binary = true)

    lazy val `asc`: MediaType =
      MediaType("audio", "asc", compressible = false, binary = true)

    lazy val `atrac-advanced-lossless`: MediaType =
      MediaType("audio", "atrac-advanced-lossless", compressible = false, binary = true)

    lazy val `atrac-x`: MediaType =
      MediaType("audio", "atrac-x", compressible = false, binary = true)

    lazy val `atrac3`: MediaType =
      MediaType("audio", "atrac3", compressible = false, binary = true)

    lazy val `basic`: MediaType =
      MediaType("audio", "basic", compressible = false, binary = true, fileExtensions = List("au", "snd"))

    lazy val `bv16`: MediaType =
      MediaType("audio", "bv16", compressible = false, binary = true)

    lazy val `bv32`: MediaType =
      MediaType("audio", "bv32", compressible = false, binary = true)

    lazy val `clearmode`: MediaType =
      MediaType("audio", "clearmode", compressible = false, binary = true)

    lazy val `cn`: MediaType =
      MediaType("audio", "cn", compressible = false, binary = true)

    lazy val `dat12`: MediaType =
      MediaType("audio", "dat12", compressible = false, binary = true)

    lazy val `dls`: MediaType =
      MediaType("audio", "dls", compressible = false, binary = true)

    lazy val `dsr-es201108`: MediaType =
      MediaType("audio", "dsr-es201108", compressible = false, binary = true)

    lazy val `dsr-es202050`: MediaType =
      MediaType("audio", "dsr-es202050", compressible = false, binary = true)

    lazy val `dsr-es202211`: MediaType =
      MediaType("audio", "dsr-es202211", compressible = false, binary = true)

    lazy val `dsr-es202212`: MediaType =
      MediaType("audio", "dsr-es202212", compressible = false, binary = true)

    lazy val `dv`: MediaType =
      MediaType("audio", "dv", compressible = false, binary = true)

    lazy val `dvi4`: MediaType =
      MediaType("audio", "dvi4", compressible = false, binary = true)

    lazy val `eac3`: MediaType =
      MediaType("audio", "eac3", compressible = false, binary = true)

    lazy val `encaprtp`: MediaType =
      MediaType("audio", "encaprtp", compressible = false, binary = true)

    lazy val `evrc`: MediaType =
      MediaType("audio", "evrc", compressible = false, binary = true)

    lazy val `evrc-qcp`: MediaType =
      MediaType("audio", "evrc-qcp", compressible = false, binary = true)

    lazy val `evrc0`: MediaType =
      MediaType("audio", "evrc0", compressible = false, binary = true)

    lazy val `evrc1`: MediaType =
      MediaType("audio", "evrc1", compressible = false, binary = true)

    lazy val `evrcb`: MediaType =
      MediaType("audio", "evrcb", compressible = false, binary = true)

    lazy val `evrcb0`: MediaType =
      MediaType("audio", "evrcb0", compressible = false, binary = true)

    lazy val `evrcb1`: MediaType =
      MediaType("audio", "evrcb1", compressible = false, binary = true)

    lazy val `evrcnw`: MediaType =
      MediaType("audio", "evrcnw", compressible = false, binary = true)

    lazy val `evrcnw0`: MediaType =
      MediaType("audio", "evrcnw0", compressible = false, binary = true)

    lazy val `evrcnw1`: MediaType =
      MediaType("audio", "evrcnw1", compressible = false, binary = true)

    lazy val `evrcwb`: MediaType =
      MediaType("audio", "evrcwb", compressible = false, binary = true)

    lazy val `evrcwb0`: MediaType =
      MediaType("audio", "evrcwb0", compressible = false, binary = true)

    lazy val `evrcwb1`: MediaType =
      MediaType("audio", "evrcwb1", compressible = false, binary = true)

    lazy val `evs`: MediaType =
      MediaType("audio", "evs", compressible = false, binary = true)

    lazy val `flac`: MediaType =
      MediaType("audio", "flac", compressible = false, binary = true)

    lazy val `flexfec`: MediaType =
      MediaType("audio", "flexfec", compressible = false, binary = true)

    lazy val `fwdred`: MediaType =
      MediaType("audio", "fwdred", compressible = false, binary = true)

    lazy val `g711-0`: MediaType =
      MediaType("audio", "g711-0", compressible = false, binary = true)

    lazy val `g719`: MediaType =
      MediaType("audio", "g719", compressible = false, binary = true)

    lazy val `g722`: MediaType =
      MediaType("audio", "g722", compressible = false, binary = true)

    lazy val `g7221`: MediaType =
      MediaType("audio", "g7221", compressible = false, binary = true)

    lazy val `g723`: MediaType =
      MediaType("audio", "g723", compressible = false, binary = true)

    lazy val `g726-16`: MediaType =
      MediaType("audio", "g726-16", compressible = false, binary = true)

    lazy val `g726-24`: MediaType =
      MediaType("audio", "g726-24", compressible = false, binary = true)

    lazy val `g726-32`: MediaType =
      MediaType("audio", "g726-32", compressible = false, binary = true)

    lazy val `g726-40`: MediaType =
      MediaType("audio", "g726-40", compressible = false, binary = true)

    lazy val `g728`: MediaType =
      MediaType("audio", "g728", compressible = false, binary = true)

    lazy val `g729`: MediaType =
      MediaType("audio", "g729", compressible = false, binary = true)

    lazy val `g7291`: MediaType =
      MediaType("audio", "g7291", compressible = false, binary = true)

    lazy val `g729d`: MediaType =
      MediaType("audio", "g729d", compressible = false, binary = true)

    lazy val `g729e`: MediaType =
      MediaType("audio", "g729e", compressible = false, binary = true)

    lazy val `gsm`: MediaType =
      MediaType("audio", "gsm", compressible = false, binary = true)

    lazy val `gsm-efr`: MediaType =
      MediaType("audio", "gsm-efr", compressible = false, binary = true)

    lazy val `gsm-hr-08`: MediaType =
      MediaType("audio", "gsm-hr-08", compressible = false, binary = true)

    lazy val `ilbc`: MediaType =
      MediaType("audio", "ilbc", compressible = false, binary = true)

    lazy val `ip-mr_v2.5`: MediaType =
      MediaType("audio", "ip-mr_v2.5", compressible = false, binary = true)

    lazy val `isac`: MediaType =
      MediaType("audio", "isac", compressible = false, binary = true)

    lazy val `l16`: MediaType =
      MediaType("audio", "l16", compressible = false, binary = true)

    lazy val `l20`: MediaType =
      MediaType("audio", "l20", compressible = false, binary = true)

    lazy val `l24`: MediaType =
      MediaType("audio", "l24", compressible = false, binary = true)

    lazy val `l8`: MediaType =
      MediaType("audio", "l8", compressible = false, binary = true)

    lazy val `lpc`: MediaType =
      MediaType("audio", "lpc", compressible = false, binary = true)

    lazy val `matroska`: MediaType =
      MediaType("audio", "matroska", compressible = false, binary = true, fileExtensions = List("mka"))

    lazy val `melp`: MediaType =
      MediaType("audio", "melp", compressible = false, binary = true)

    lazy val `melp1200`: MediaType =
      MediaType("audio", "melp1200", compressible = false, binary = true)

    lazy val `melp2400`: MediaType =
      MediaType("audio", "melp2400", compressible = false, binary = true)

    lazy val `melp600`: MediaType =
      MediaType("audio", "melp600", compressible = false, binary = true)

    lazy val `mhas`: MediaType =
      MediaType("audio", "mhas", compressible = false, binary = true)

    lazy val `midi`: MediaType =
      MediaType(
        "audio",
        "midi",
        compressible = false,
        binary = true,
        fileExtensions = List("mid", "midi", "kar", "rmi")
      )

    lazy val `midi-clip`: MediaType =
      MediaType("audio", "midi-clip", compressible = false, binary = true)

    lazy val `mobile-xmf`: MediaType =
      MediaType("audio", "mobile-xmf", compressible = false, binary = true, fileExtensions = List("mxmf"))

    lazy val `mp3`: MediaType =
      MediaType("audio", "mp3", compressible = false, binary = true, fileExtensions = List("mp3"))

    lazy val `mp4`: MediaType =
      MediaType("audio", "mp4", compressible = false, binary = true, fileExtensions = List("m4a", "mp4a", "m4b"))

    lazy val `mp4a-latm`: MediaType =
      MediaType("audio", "mp4a-latm", compressible = false, binary = true)

    lazy val `mpa`: MediaType =
      MediaType("audio", "mpa", compressible = false, binary = true)

    lazy val `mpa-robust`: MediaType =
      MediaType("audio", "mpa-robust", compressible = false, binary = true)

    lazy val `mpeg`: MediaType =
      MediaType(
        "audio",
        "mpeg",
        compressible = false,
        binary = true,
        fileExtensions = List("mpga", "mp2", "mp2a", "mp3", "m2a", "m3a")
      )

    lazy val `mpeg4-generic`: MediaType =
      MediaType("audio", "mpeg4-generic", compressible = false, binary = true)

    lazy val `musepack`: MediaType =
      MediaType("audio", "musepack", compressible = false, binary = true)

    lazy val `ogg`: MediaType =
      MediaType("audio", "ogg", compressible = false, binary = true, fileExtensions = List("oga", "ogg", "spx", "opus"))

    lazy val `opus`: MediaType =
      MediaType("audio", "opus", compressible = false, binary = true)

    lazy val `parityfec`: MediaType =
      MediaType("audio", "parityfec", compressible = false, binary = true)

    lazy val `pcma`: MediaType =
      MediaType("audio", "pcma", compressible = false, binary = true)

    lazy val `pcma-wb`: MediaType =
      MediaType("audio", "pcma-wb", compressible = false, binary = true)

    lazy val `pcmu`: MediaType =
      MediaType("audio", "pcmu", compressible = false, binary = true)

    lazy val `pcmu-wb`: MediaType =
      MediaType("audio", "pcmu-wb", compressible = false, binary = true)

    lazy val `prs.sid`: MediaType =
      MediaType("audio", "prs.sid", compressible = false, binary = true)

    lazy val `qcelp`: MediaType =
      MediaType("audio", "qcelp", compressible = false, binary = true)

    lazy val `raptorfec`: MediaType =
      MediaType("audio", "raptorfec", compressible = false, binary = true)

    lazy val `red`: MediaType =
      MediaType("audio", "red", compressible = false, binary = true)

    lazy val `rtp-enc-aescm128`: MediaType =
      MediaType("audio", "rtp-enc-aescm128", compressible = false, binary = true)

    lazy val `rtp-midi`: MediaType =
      MediaType("audio", "rtp-midi", compressible = false, binary = true)

    lazy val `rtploopback`: MediaType =
      MediaType("audio", "rtploopback", compressible = false, binary = true)

    lazy val `rtx`: MediaType =
      MediaType("audio", "rtx", compressible = false, binary = true)

    lazy val `s3m`: MediaType =
      MediaType("audio", "s3m", compressible = false, binary = true, fileExtensions = List("s3m"))

    lazy val `scip`: MediaType =
      MediaType("audio", "scip", compressible = false, binary = true)

    lazy val `silk`: MediaType =
      MediaType("audio", "silk", compressible = false, binary = true, fileExtensions = List("sil"))

    lazy val `smv`: MediaType =
      MediaType("audio", "smv", compressible = false, binary = true)

    lazy val `smv-qcp`: MediaType =
      MediaType("audio", "smv-qcp", compressible = false, binary = true)

    lazy val `smv0`: MediaType =
      MediaType("audio", "smv0", compressible = false, binary = true)

    lazy val `sofa`: MediaType =
      MediaType("audio", "sofa", compressible = false, binary = true)

    lazy val `sp-midi`: MediaType =
      MediaType("audio", "sp-midi", compressible = false, binary = true)

    lazy val `speex`: MediaType =
      MediaType("audio", "speex", compressible = false, binary = true)

    lazy val `t140c`: MediaType =
      MediaType("audio", "t140c", compressible = false, binary = true)

    lazy val `t38`: MediaType =
      MediaType("audio", "t38", compressible = false, binary = true)

    lazy val `telephone-event`: MediaType =
      MediaType("audio", "telephone-event", compressible = false, binary = true)

    lazy val `tetra_acelp`: MediaType =
      MediaType("audio", "tetra_acelp", compressible = false, binary = true)

    lazy val `tetra_acelp_bb`: MediaType =
      MediaType("audio", "tetra_acelp_bb", compressible = false, binary = true)

    lazy val `tone`: MediaType =
      MediaType("audio", "tone", compressible = false, binary = true)

    lazy val `tsvcis`: MediaType =
      MediaType("audio", "tsvcis", compressible = false, binary = true)

    lazy val `uemclip`: MediaType =
      MediaType("audio", "uemclip", compressible = false, binary = true)

    lazy val `ulpfec`: MediaType =
      MediaType("audio", "ulpfec", compressible = false, binary = true)

    lazy val `usac`: MediaType =
      MediaType("audio", "usac", compressible = false, binary = true)

    lazy val `vdvi`: MediaType =
      MediaType("audio", "vdvi", compressible = false, binary = true)

    lazy val `vmr-wb`: MediaType =
      MediaType("audio", "vmr-wb", compressible = false, binary = true)

    lazy val `vnd.3gpp.iufp`: MediaType =
      MediaType("audio", "vnd.3gpp.iufp", compressible = false, binary = true)

    lazy val `vnd.4sb`: MediaType =
      MediaType("audio", "vnd.4sb", compressible = false, binary = true)

    lazy val `vnd.audiokoz`: MediaType =
      MediaType("audio", "vnd.audiokoz", compressible = false, binary = true)

    lazy val `vnd.blockfact.facta`: MediaType =
      MediaType("audio", "vnd.blockfact.facta", compressible = false, binary = true)

    lazy val `vnd.celp`: MediaType =
      MediaType("audio", "vnd.celp", compressible = false, binary = true)

    lazy val `vnd.cisco.nse`: MediaType =
      MediaType("audio", "vnd.cisco.nse", compressible = false, binary = true)

    lazy val `vnd.cmles.radio-events`: MediaType =
      MediaType("audio", "vnd.cmles.radio-events", compressible = false, binary = true)

    lazy val `vnd.cns.anp1`: MediaType =
      MediaType("audio", "vnd.cns.anp1", compressible = false, binary = true)

    lazy val `vnd.cns.inf1`: MediaType =
      MediaType("audio", "vnd.cns.inf1", compressible = false, binary = true)

    lazy val `vnd.dece.audio`: MediaType =
      MediaType("audio", "vnd.dece.audio", compressible = false, binary = true, fileExtensions = List("uva", "uvva"))

    lazy val `vnd.digital-winds`: MediaType =
      MediaType("audio", "vnd.digital-winds", compressible = false, binary = true, fileExtensions = List("eol"))

    lazy val `vnd.dlna.adts`: MediaType =
      MediaType("audio", "vnd.dlna.adts", compressible = false, binary = true)

    lazy val `vnd.dolby.heaac.1`: MediaType =
      MediaType("audio", "vnd.dolby.heaac.1", compressible = false, binary = true)

    lazy val `vnd.dolby.heaac.2`: MediaType =
      MediaType("audio", "vnd.dolby.heaac.2", compressible = false, binary = true)

    lazy val `vnd.dolby.mlp`: MediaType =
      MediaType("audio", "vnd.dolby.mlp", compressible = false, binary = true)

    lazy val `vnd.dolby.mps`: MediaType =
      MediaType("audio", "vnd.dolby.mps", compressible = false, binary = true)

    lazy val `vnd.dolby.pl2`: MediaType =
      MediaType("audio", "vnd.dolby.pl2", compressible = false, binary = true)

    lazy val `vnd.dolby.pl2x`: MediaType =
      MediaType("audio", "vnd.dolby.pl2x", compressible = false, binary = true)

    lazy val `vnd.dolby.pl2z`: MediaType =
      MediaType("audio", "vnd.dolby.pl2z", compressible = false, binary = true)

    lazy val `vnd.dolby.pulse.1`: MediaType =
      MediaType("audio", "vnd.dolby.pulse.1", compressible = false, binary = true)

    lazy val `vnd.dra`: MediaType =
      MediaType("audio", "vnd.dra", compressible = false, binary = true, fileExtensions = List("dra"))

    lazy val `vnd.dts`: MediaType =
      MediaType("audio", "vnd.dts", compressible = false, binary = true, fileExtensions = List("dts"))

    lazy val `vnd.dts.hd`: MediaType =
      MediaType("audio", "vnd.dts.hd", compressible = false, binary = true, fileExtensions = List("dtshd"))

    lazy val `vnd.dts.uhd`: MediaType =
      MediaType("audio", "vnd.dts.uhd", compressible = false, binary = true)

    lazy val `vnd.dvb.file`: MediaType =
      MediaType("audio", "vnd.dvb.file", compressible = false, binary = true)

    lazy val `vnd.everad.plj`: MediaType =
      MediaType("audio", "vnd.everad.plj", compressible = false, binary = true)

    lazy val `vnd.hns.audio`: MediaType =
      MediaType("audio", "vnd.hns.audio", compressible = false, binary = true)

    lazy val `vnd.lucent.voice`: MediaType =
      MediaType("audio", "vnd.lucent.voice", compressible = false, binary = true, fileExtensions = List("lvp"))

    lazy val `vnd.ms-playready.media.pya`: MediaType =
      MediaType(
        "audio",
        "vnd.ms-playready.media.pya",
        compressible = false,
        binary = true,
        fileExtensions = List("pya")
      )

    lazy val `vnd.nokia.mobile-xmf`: MediaType =
      MediaType("audio", "vnd.nokia.mobile-xmf", compressible = false, binary = true)

    lazy val `vnd.nortel.vbk`: MediaType =
      MediaType("audio", "vnd.nortel.vbk", compressible = false, binary = true)

    lazy val `vnd.nuera.ecelp4800`: MediaType =
      MediaType("audio", "vnd.nuera.ecelp4800", compressible = false, binary = true, fileExtensions = List("ecelp4800"))

    lazy val `vnd.nuera.ecelp7470`: MediaType =
      MediaType("audio", "vnd.nuera.ecelp7470", compressible = false, binary = true, fileExtensions = List("ecelp7470"))

    lazy val `vnd.nuera.ecelp9600`: MediaType =
      MediaType("audio", "vnd.nuera.ecelp9600", compressible = false, binary = true, fileExtensions = List("ecelp9600"))

    lazy val `vnd.octel.sbc`: MediaType =
      MediaType("audio", "vnd.octel.sbc", compressible = false, binary = true)

    lazy val `vnd.presonus.multitrack`: MediaType =
      MediaType("audio", "vnd.presonus.multitrack", compressible = false, binary = true)

    lazy val `vnd.qcelp`: MediaType =
      MediaType("audio", "vnd.qcelp", compressible = false, binary = true)

    lazy val `vnd.rhetorex.32kadpcm`: MediaType =
      MediaType("audio", "vnd.rhetorex.32kadpcm", compressible = false, binary = true)

    lazy val `vnd.rip`: MediaType =
      MediaType("audio", "vnd.rip", compressible = false, binary = true, fileExtensions = List("rip"))

    lazy val `vnd.rn-realaudio`: MediaType =
      MediaType("audio", "vnd.rn-realaudio", compressible = false, binary = true)

    lazy val `vnd.sealedmedia.softseal.mpeg`: MediaType =
      MediaType("audio", "vnd.sealedmedia.softseal.mpeg", compressible = false, binary = true)

    lazy val `vnd.vmx.cvsd`: MediaType =
      MediaType("audio", "vnd.vmx.cvsd", compressible = false, binary = true)

    lazy val `vnd.wave`: MediaType =
      MediaType("audio", "vnd.wave", compressible = false, binary = true)

    lazy val `vorbis`: MediaType =
      MediaType("audio", "vorbis", compressible = false, binary = true)

    lazy val `vorbis-config`: MediaType =
      MediaType("audio", "vorbis-config", compressible = false, binary = true)

    lazy val `wav`: MediaType =
      MediaType("audio", "wav", compressible = false, binary = true, fileExtensions = List("wav"))

    lazy val `wave`: MediaType =
      MediaType("audio", "wave", compressible = false, binary = true, fileExtensions = List("wav"))

    lazy val `webm`: MediaType =
      MediaType("audio", "webm", compressible = false, binary = true, fileExtensions = List("weba"))

    lazy val `x-aac`: MediaType =
      MediaType("audio", "x-aac", compressible = false, binary = true, fileExtensions = List("aac"))

    lazy val `x-aiff`: MediaType =
      MediaType("audio", "x-aiff", compressible = false, binary = true, fileExtensions = List("aif", "aiff", "aifc"))

    lazy val `x-caf`: MediaType =
      MediaType("audio", "x-caf", compressible = false, binary = true, fileExtensions = List("caf"))

    lazy val `x-flac`: MediaType =
      MediaType("audio", "x-flac", compressible = false, binary = true, fileExtensions = List("flac"))

    lazy val `x-m4a`: MediaType =
      MediaType("audio", "x-m4a", compressible = false, binary = true, fileExtensions = List("m4a"))

    lazy val `x-matroska`: MediaType =
      MediaType("audio", "x-matroska", compressible = false, binary = true, fileExtensions = List("mka"))

    lazy val `x-mpegurl`: MediaType =
      MediaType("audio", "x-mpegurl", compressible = false, binary = true, fileExtensions = List("m3u"))

    lazy val `x-ms-wax`: MediaType =
      MediaType("audio", "x-ms-wax", compressible = false, binary = true, fileExtensions = List("wax"))

    lazy val `x-ms-wma`: MediaType =
      MediaType("audio", "x-ms-wma", compressible = false, binary = true, fileExtensions = List("wma"))

    lazy val `x-pn-realaudio`: MediaType =
      MediaType("audio", "x-pn-realaudio", compressible = false, binary = true, fileExtensions = List("ram", "ra"))

    lazy val `x-pn-realaudio-plugin`: MediaType =
      MediaType("audio", "x-pn-realaudio-plugin", compressible = false, binary = true, fileExtensions = List("rmp"))

    lazy val `x-realaudio`: MediaType =
      MediaType("audio", "x-realaudio", compressible = false, binary = true, fileExtensions = List("ra"))

    lazy val `x-tta`: MediaType =
      MediaType("audio", "x-tta", compressible = false, binary = true)

    lazy val `x-wav`: MediaType =
      MediaType("audio", "x-wav", compressible = false, binary = true, fileExtensions = List("wav"))

    lazy val `xm`: MediaType =
      MediaType("audio", "xm", compressible = false, binary = true, fileExtensions = List("xm"))

    lazy val all: List[MediaType] = List(
      `1d-interleaved-parityfec`,
      `32kadpcm`,
      `3gpp`,
      `3gpp2`,
      `aac`,
      `ac3`,
      `adpcm`,
      `amr`,
      `amr-wb`,
      `amr-wb+`,
      `aptx`,
      `asc`,
      `atrac-advanced-lossless`,
      `atrac-x`,
      `atrac3`,
      `basic`,
      `bv16`,
      `bv32`,
      `clearmode`,
      `cn`,
      `dat12`,
      `dls`,
      `dsr-es201108`,
      `dsr-es202050`,
      `dsr-es202211`,
      `dsr-es202212`,
      `dv`,
      `dvi4`,
      `eac3`,
      `encaprtp`,
      `evrc`,
      `evrc-qcp`,
      `evrc0`,
      `evrc1`,
      `evrcb`,
      `evrcb0`,
      `evrcb1`,
      `evrcnw`,
      `evrcnw0`,
      `evrcnw1`,
      `evrcwb`,
      `evrcwb0`,
      `evrcwb1`,
      `evs`,
      `flac`,
      `flexfec`,
      `fwdred`,
      `g711-0`,
      `g719`,
      `g722`,
      `g7221`,
      `g723`,
      `g726-16`,
      `g726-24`,
      `g726-32`,
      `g726-40`,
      `g728`,
      `g729`,
      `g7291`,
      `g729d`,
      `g729e`,
      `gsm`,
      `gsm-efr`,
      `gsm-hr-08`,
      `ilbc`,
      `ip-mr_v2.5`,
      `isac`,
      `l16`,
      `l20`,
      `l24`,
      `l8`,
      `lpc`,
      `matroska`,
      `melp`,
      `melp1200`,
      `melp2400`,
      `melp600`,
      `mhas`,
      `midi`,
      `midi-clip`,
      `mobile-xmf`,
      `mp3`,
      `mp4`,
      `mp4a-latm`,
      `mpa`,
      `mpa-robust`,
      `mpeg`,
      `mpeg4-generic`,
      `musepack`,
      `ogg`,
      `opus`,
      `parityfec`,
      `pcma`,
      `pcma-wb`,
      `pcmu`,
      `pcmu-wb`,
      `prs.sid`,
      `qcelp`,
      `raptorfec`,
      `red`,
      `rtp-enc-aescm128`,
      `rtp-midi`,
      `rtploopback`,
      `rtx`,
      `s3m`,
      `scip`,
      `silk`,
      `smv`,
      `smv-qcp`,
      `smv0`,
      `sofa`,
      `sp-midi`,
      `speex`,
      `t140c`,
      `t38`,
      `telephone-event`,
      `tetra_acelp`,
      `tetra_acelp_bb`,
      `tone`,
      `tsvcis`,
      `uemclip`,
      `ulpfec`,
      `usac`,
      `vdvi`,
      `vmr-wb`,
      `vnd.3gpp.iufp`,
      `vnd.4sb`,
      `vnd.audiokoz`,
      `vnd.blockfact.facta`,
      `vnd.celp`,
      `vnd.cisco.nse`,
      `vnd.cmles.radio-events`,
      `vnd.cns.anp1`,
      `vnd.cns.inf1`,
      `vnd.dece.audio`,
      `vnd.digital-winds`,
      `vnd.dlna.adts`,
      `vnd.dolby.heaac.1`,
      `vnd.dolby.heaac.2`,
      `vnd.dolby.mlp`,
      `vnd.dolby.mps`,
      `vnd.dolby.pl2`,
      `vnd.dolby.pl2x`,
      `vnd.dolby.pl2z`,
      `vnd.dolby.pulse.1`,
      `vnd.dra`,
      `vnd.dts`,
      `vnd.dts.hd`,
      `vnd.dts.uhd`,
      `vnd.dvb.file`,
      `vnd.everad.plj`,
      `vnd.hns.audio`,
      `vnd.lucent.voice`,
      `vnd.ms-playready.media.pya`,
      `vnd.nokia.mobile-xmf`,
      `vnd.nortel.vbk`,
      `vnd.nuera.ecelp4800`,
      `vnd.nuera.ecelp7470`,
      `vnd.nuera.ecelp9600`,
      `vnd.octel.sbc`,
      `vnd.presonus.multitrack`,
      `vnd.qcelp`,
      `vnd.rhetorex.32kadpcm`,
      `vnd.rip`,
      `vnd.rn-realaudio`,
      `vnd.sealedmedia.softseal.mpeg`,
      `vnd.vmx.cvsd`,
      `vnd.wave`,
      `vorbis`,
      `vorbis-config`,
      `wav`,
      `wave`,
      `webm`,
      `x-aac`,
      `x-aiff`,
      `x-caf`,
      `x-flac`,
      `x-m4a`,
      `x-matroska`,
      `x-mpegurl`,
      `x-ms-wax`,
      `x-ms-wma`,
      `x-pn-realaudio`,
      `x-pn-realaudio-plugin`,
      `x-realaudio`,
      `x-tta`,
      `x-wav`,
      `xm`
    )
  }

  object chemical {
    lazy val `x-cdx`: MediaType =
      MediaType("chemical", "x-cdx", compressible = false, binary = true, fileExtensions = List("cdx"))

    lazy val `x-cif`: MediaType =
      MediaType("chemical", "x-cif", compressible = false, binary = true, fileExtensions = List("cif"))

    lazy val `x-cmdf`: MediaType =
      MediaType("chemical", "x-cmdf", compressible = false, binary = true, fileExtensions = List("cmdf"))

    lazy val `x-cml`: MediaType =
      MediaType("chemical", "x-cml", compressible = false, binary = true, fileExtensions = List("cml"))

    lazy val `x-csml`: MediaType =
      MediaType("chemical", "x-csml", compressible = false, binary = true, fileExtensions = List("csml"))

    lazy val `x-pdb`: MediaType =
      MediaType("chemical", "x-pdb", compressible = false, binary = true)

    lazy val `x-xyz`: MediaType =
      MediaType("chemical", "x-xyz", compressible = false, binary = true, fileExtensions = List("xyz"))

    lazy val all: List[MediaType] = List(
      `x-cdx`,
      `x-cif`,
      `x-cmdf`,
      `x-cml`,
      `x-csml`,
      `x-pdb`,
      `x-xyz`
    )
  }

  object font {
    lazy val `collection`: MediaType =
      MediaType("font", "collection", compressible = false, binary = true, fileExtensions = List("ttc"))

    lazy val `otf`: MediaType =
      MediaType("font", "otf", compressible = true, binary = true, fileExtensions = List("otf"))

    lazy val `sfnt`: MediaType =
      MediaType("font", "sfnt", compressible = false, binary = true)

    lazy val `ttf`: MediaType =
      MediaType("font", "ttf", compressible = true, binary = true, fileExtensions = List("ttf"))

    lazy val `woff`: MediaType =
      MediaType("font", "woff", compressible = false, binary = true, fileExtensions = List("woff"))

    lazy val `woff2`: MediaType =
      MediaType("font", "woff2", compressible = false, binary = true, fileExtensions = List("woff2"))

    lazy val all: List[MediaType] = List(
      `collection`,
      `otf`,
      `sfnt`,
      `ttf`,
      `woff`,
      `woff2`
    )
  }

  object image {
    lazy val `aces`: MediaType =
      MediaType("image", "aces", compressible = false, binary = true, fileExtensions = List("exr"))

    lazy val `apng`: MediaType =
      MediaType("image", "apng", compressible = false, binary = true, fileExtensions = List("apng"))

    lazy val `avci`: MediaType =
      MediaType("image", "avci", compressible = false, binary = true, fileExtensions = List("avci"))

    lazy val `avcs`: MediaType =
      MediaType("image", "avcs", compressible = false, binary = true, fileExtensions = List("avcs"))

    lazy val `avif`: MediaType =
      MediaType("image", "avif", compressible = false, binary = true, fileExtensions = List("avif"))

    lazy val `bmp`: MediaType =
      MediaType("image", "bmp", compressible = true, binary = true, fileExtensions = List("bmp", "dib"))

    lazy val `cgm`: MediaType =
      MediaType("image", "cgm", compressible = false, binary = true, fileExtensions = List("cgm"))

    lazy val `dicom-rle`: MediaType =
      MediaType("image", "dicom-rle", compressible = false, binary = true, fileExtensions = List("drle"))

    lazy val `dpx`: MediaType =
      MediaType("image", "dpx", compressible = false, binary = true, fileExtensions = List("dpx"))

    lazy val `emf`: MediaType =
      MediaType("image", "emf", compressible = false, binary = true, fileExtensions = List("emf"))

    lazy val `fits`: MediaType =
      MediaType("image", "fits", compressible = false, binary = true, fileExtensions = List("fits"))

    lazy val `g3fax`: MediaType =
      MediaType("image", "g3fax", compressible = false, binary = true, fileExtensions = List("g3"))

    lazy val `gif`: MediaType =
      MediaType("image", "gif", compressible = false, binary = true, fileExtensions = List("gif"))

    lazy val `heic`: MediaType =
      MediaType("image", "heic", compressible = false, binary = true, fileExtensions = List("heic"))

    lazy val `heic-sequence`: MediaType =
      MediaType("image", "heic-sequence", compressible = false, binary = true, fileExtensions = List("heics"))

    lazy val `heif`: MediaType =
      MediaType("image", "heif", compressible = false, binary = true, fileExtensions = List("heif"))

    lazy val `heif-sequence`: MediaType =
      MediaType("image", "heif-sequence", compressible = false, binary = true, fileExtensions = List("heifs"))

    lazy val `hej2k`: MediaType =
      MediaType("image", "hej2k", compressible = false, binary = true, fileExtensions = List("hej2"))

    lazy val `ief`: MediaType =
      MediaType("image", "ief", compressible = false, binary = true, fileExtensions = List("ief"))

    lazy val `j2c`: MediaType =
      MediaType("image", "j2c", compressible = false, binary = true)

    lazy val `jaii`: MediaType =
      MediaType("image", "jaii", compressible = false, binary = true, fileExtensions = List("jaii"))

    lazy val `jais`: MediaType =
      MediaType("image", "jais", compressible = false, binary = true, fileExtensions = List("jais"))

    lazy val `jls`: MediaType =
      MediaType("image", "jls", compressible = false, binary = true, fileExtensions = List("jls"))

    lazy val `jp2`: MediaType =
      MediaType("image", "jp2", compressible = false, binary = true, fileExtensions = List("jp2", "jpg2"))

    lazy val `jpeg`: MediaType =
      MediaType("image", "jpeg", compressible = false, binary = true, fileExtensions = List("jpg", "jpeg", "jpe"))

    lazy val `jph`: MediaType =
      MediaType("image", "jph", compressible = false, binary = true, fileExtensions = List("jph"))

    lazy val `jphc`: MediaType =
      MediaType("image", "jphc", compressible = false, binary = true, fileExtensions = List("jhc"))

    lazy val `jpm`: MediaType =
      MediaType("image", "jpm", compressible = false, binary = true, fileExtensions = List("jpm", "jpgm"))

    lazy val `jpx`: MediaType =
      MediaType("image", "jpx", compressible = false, binary = true, fileExtensions = List("jpx", "jpf"))

    lazy val `jxl`: MediaType =
      MediaType("image", "jxl", compressible = false, binary = true, fileExtensions = List("jxl"))

    lazy val `jxr`: MediaType =
      MediaType("image", "jxr", compressible = false, binary = true, fileExtensions = List("jxr"))

    lazy val `jxra`: MediaType =
      MediaType("image", "jxra", compressible = false, binary = true, fileExtensions = List("jxra"))

    lazy val `jxrs`: MediaType =
      MediaType("image", "jxrs", compressible = false, binary = true, fileExtensions = List("jxrs"))

    lazy val `jxs`: MediaType =
      MediaType("image", "jxs", compressible = false, binary = true, fileExtensions = List("jxs"))

    lazy val `jxsc`: MediaType =
      MediaType("image", "jxsc", compressible = false, binary = true, fileExtensions = List("jxsc"))

    lazy val `jxsi`: MediaType =
      MediaType("image", "jxsi", compressible = false, binary = true, fileExtensions = List("jxsi"))

    lazy val `jxss`: MediaType =
      MediaType("image", "jxss", compressible = false, binary = true, fileExtensions = List("jxss"))

    lazy val `ktx`: MediaType =
      MediaType("image", "ktx", compressible = false, binary = true, fileExtensions = List("ktx"))

    lazy val `ktx2`: MediaType =
      MediaType("image", "ktx2", compressible = false, binary = true, fileExtensions = List("ktx2"))

    lazy val `naplps`: MediaType =
      MediaType("image", "naplps", compressible = false, binary = true)

    lazy val `pjpeg`: MediaType =
      MediaType("image", "pjpeg", compressible = false, binary = true, fileExtensions = List("jfif"))

    lazy val `png`: MediaType =
      MediaType("image", "png", compressible = false, binary = true, fileExtensions = List("png"))

    lazy val `prs.btif`: MediaType =
      MediaType("image", "prs.btif", compressible = false, binary = true, fileExtensions = List("btif", "btf"))

    lazy val `prs.pti`: MediaType =
      MediaType("image", "prs.pti", compressible = false, binary = true, fileExtensions = List("pti"))

    lazy val `pwg-raster`: MediaType =
      MediaType("image", "pwg-raster", compressible = false, binary = true)

    lazy val `sgi`: MediaType =
      MediaType("image", "sgi", compressible = false, binary = true, fileExtensions = List("sgi"))

    lazy val `svg+xml`: MediaType =
      MediaType("image", "svg+xml", compressible = true, binary = true, fileExtensions = List("svg", "svgz"))

    lazy val `t38`: MediaType =
      MediaType("image", "t38", compressible = false, binary = true, fileExtensions = List("t38"))

    lazy val `tiff`: MediaType =
      MediaType("image", "tiff", compressible = false, binary = true, fileExtensions = List("tif", "tiff"))

    lazy val `tiff-fx`: MediaType =
      MediaType("image", "tiff-fx", compressible = false, binary = true, fileExtensions = List("tfx"))

    lazy val `vnd.adobe.photoshop`: MediaType =
      MediaType("image", "vnd.adobe.photoshop", compressible = true, binary = true, fileExtensions = List("psd"))

    lazy val `vnd.airzip.accelerator.azv`: MediaType =
      MediaType(
        "image",
        "vnd.airzip.accelerator.azv",
        compressible = false,
        binary = true,
        fileExtensions = List("azv")
      )

    lazy val `vnd.blockfact.facti`: MediaType =
      MediaType("image", "vnd.blockfact.facti", compressible = false, binary = true, fileExtensions = List("facti"))

    lazy val `vnd.clip`: MediaType =
      MediaType("image", "vnd.clip", compressible = false, binary = true)

    lazy val `vnd.cns.inf2`: MediaType =
      MediaType("image", "vnd.cns.inf2", compressible = false, binary = true)

    lazy val `vnd.dece.graphic`: MediaType =
      MediaType(
        "image",
        "vnd.dece.graphic",
        compressible = false,
        binary = true,
        fileExtensions = List("uvi", "uvvi", "uvg", "uvvg")
      )

    lazy val `vnd.djvu`: MediaType =
      MediaType("image", "vnd.djvu", compressible = false, binary = true, fileExtensions = List("djvu", "djv"))

    lazy val `vnd.dvb.subtitle`: MediaType =
      MediaType("image", "vnd.dvb.subtitle", compressible = false, binary = true, fileExtensions = List("sub"))

    lazy val `vnd.dwg`: MediaType =
      MediaType("image", "vnd.dwg", compressible = false, binary = true, fileExtensions = List("dwg"))

    lazy val `vnd.dxf`: MediaType =
      MediaType("image", "vnd.dxf", compressible = false, binary = true, fileExtensions = List("dxf"))

    lazy val `vnd.fastbidsheet`: MediaType =
      MediaType("image", "vnd.fastbidsheet", compressible = false, binary = true, fileExtensions = List("fbs"))

    lazy val `vnd.fpx`: MediaType =
      MediaType("image", "vnd.fpx", compressible = false, binary = true, fileExtensions = List("fpx"))

    lazy val `vnd.fst`: MediaType =
      MediaType("image", "vnd.fst", compressible = false, binary = true, fileExtensions = List("fst"))

    lazy val `vnd.fujixerox.edmics-mmr`: MediaType =
      MediaType("image", "vnd.fujixerox.edmics-mmr", compressible = false, binary = true, fileExtensions = List("mmr"))

    lazy val `vnd.fujixerox.edmics-rlc`: MediaType =
      MediaType("image", "vnd.fujixerox.edmics-rlc", compressible = false, binary = true, fileExtensions = List("rlc"))

    lazy val `vnd.globalgraphics.pgb`: MediaType =
      MediaType("image", "vnd.globalgraphics.pgb", compressible = false, binary = true)

    lazy val `vnd.microsoft.icon`: MediaType =
      MediaType("image", "vnd.microsoft.icon", compressible = true, binary = true, fileExtensions = List("ico"))

    lazy val `vnd.mix`: MediaType =
      MediaType("image", "vnd.mix", compressible = false, binary = true)

    lazy val `vnd.mozilla.apng`: MediaType =
      MediaType("image", "vnd.mozilla.apng", compressible = false, binary = true)

    lazy val `vnd.ms-dds`: MediaType =
      MediaType("image", "vnd.ms-dds", compressible = true, binary = true, fileExtensions = List("dds"))

    lazy val `vnd.ms-modi`: MediaType =
      MediaType("image", "vnd.ms-modi", compressible = false, binary = true, fileExtensions = List("mdi"))

    lazy val `vnd.ms-photo`: MediaType =
      MediaType("image", "vnd.ms-photo", compressible = false, binary = true, fileExtensions = List("wdp"))

    lazy val `vnd.net-fpx`: MediaType =
      MediaType("image", "vnd.net-fpx", compressible = false, binary = true, fileExtensions = List("npx"))

    lazy val `vnd.pco.b16`: MediaType =
      MediaType("image", "vnd.pco.b16", compressible = false, binary = true, fileExtensions = List("b16"))

    lazy val `vnd.radiance`: MediaType =
      MediaType("image", "vnd.radiance", compressible = false, binary = true)

    lazy val `vnd.sealed.png`: MediaType =
      MediaType("image", "vnd.sealed.png", compressible = false, binary = true)

    lazy val `vnd.sealedmedia.softseal.gif`: MediaType =
      MediaType("image", "vnd.sealedmedia.softseal.gif", compressible = false, binary = true)

    lazy val `vnd.sealedmedia.softseal.jpg`: MediaType =
      MediaType("image", "vnd.sealedmedia.softseal.jpg", compressible = false, binary = true)

    lazy val `vnd.svf`: MediaType =
      MediaType("image", "vnd.svf", compressible = false, binary = true)

    lazy val `vnd.tencent.tap`: MediaType =
      MediaType("image", "vnd.tencent.tap", compressible = false, binary = true, fileExtensions = List("tap"))

    lazy val `vnd.valve.source.texture`: MediaType =
      MediaType("image", "vnd.valve.source.texture", compressible = false, binary = true, fileExtensions = List("vtf"))

    lazy val `vnd.wap.wbmp`: MediaType =
      MediaType("image", "vnd.wap.wbmp", compressible = false, binary = true, fileExtensions = List("wbmp"))

    lazy val `vnd.xiff`: MediaType =
      MediaType("image", "vnd.xiff", compressible = false, binary = true, fileExtensions = List("xif"))

    lazy val `vnd.zbrush.pcx`: MediaType =
      MediaType("image", "vnd.zbrush.pcx", compressible = false, binary = true, fileExtensions = List("pcx"))

    lazy val `webp`: MediaType =
      MediaType("image", "webp", compressible = false, binary = true, fileExtensions = List("webp"))

    lazy val `wmf`: MediaType =
      MediaType("image", "wmf", compressible = false, binary = true, fileExtensions = List("wmf"))

    lazy val `x-3ds`: MediaType =
      MediaType("image", "x-3ds", compressible = false, binary = true, fileExtensions = List("3ds"))

    lazy val `x-adobe-dng`: MediaType =
      MediaType("image", "x-adobe-dng", compressible = false, binary = true, fileExtensions = List("dng"))

    lazy val `x-cmu-raster`: MediaType =
      MediaType("image", "x-cmu-raster", compressible = false, binary = true, fileExtensions = List("ras"))

    lazy val `x-cmx`: MediaType =
      MediaType("image", "x-cmx", compressible = false, binary = true, fileExtensions = List("cmx"))

    lazy val `x-emf`: MediaType =
      MediaType("image", "x-emf", compressible = false, binary = true)

    lazy val `x-freehand`: MediaType =
      MediaType(
        "image",
        "x-freehand",
        compressible = false,
        binary = true,
        fileExtensions = List("fh", "fhc", "fh4", "fh5", "fh7")
      )

    lazy val `x-icon`: MediaType =
      MediaType("image", "x-icon", compressible = true, binary = true, fileExtensions = List("ico"))

    lazy val `x-jng`: MediaType =
      MediaType("image", "x-jng", compressible = false, binary = true, fileExtensions = List("jng"))

    lazy val `x-mrsid-image`: MediaType =
      MediaType("image", "x-mrsid-image", compressible = false, binary = true, fileExtensions = List("sid"))

    lazy val `x-ms-bmp`: MediaType =
      MediaType("image", "x-ms-bmp", compressible = true, binary = true, fileExtensions = List("bmp"))

    lazy val `x-pcx`: MediaType =
      MediaType("image", "x-pcx", compressible = false, binary = true, fileExtensions = List("pcx"))

    lazy val `x-pict`: MediaType =
      MediaType("image", "x-pict", compressible = false, binary = true, fileExtensions = List("pic", "pct"))

    lazy val `x-portable-anymap`: MediaType =
      MediaType("image", "x-portable-anymap", compressible = false, binary = true, fileExtensions = List("pnm"))

    lazy val `x-portable-bitmap`: MediaType =
      MediaType("image", "x-portable-bitmap", compressible = false, binary = true, fileExtensions = List("pbm"))

    lazy val `x-portable-graymap`: MediaType =
      MediaType("image", "x-portable-graymap", compressible = false, binary = true, fileExtensions = List("pgm"))

    lazy val `x-portable-pixmap`: MediaType =
      MediaType("image", "x-portable-pixmap", compressible = false, binary = true, fileExtensions = List("ppm"))

    lazy val `x-rgb`: MediaType =
      MediaType("image", "x-rgb", compressible = false, binary = true, fileExtensions = List("rgb"))

    lazy val `x-tga`: MediaType =
      MediaType("image", "x-tga", compressible = false, binary = true, fileExtensions = List("tga"))

    lazy val `x-wmf`: MediaType =
      MediaType("image", "x-wmf", compressible = false, binary = true)

    lazy val `x-xbitmap`: MediaType =
      MediaType("image", "x-xbitmap", compressible = false, binary = true, fileExtensions = List("xbm"))

    lazy val `x-xcf`: MediaType =
      MediaType("image", "x-xcf", compressible = false, binary = true)

    lazy val `x-xpixmap`: MediaType =
      MediaType("image", "x-xpixmap", compressible = false, binary = true, fileExtensions = List("xpm"))

    lazy val `x-xwindowdump`: MediaType =
      MediaType("image", "x-xwindowdump", compressible = false, binary = true, fileExtensions = List("xwd"))

    lazy val all: List[MediaType] = List(
      `aces`,
      `apng`,
      `avci`,
      `avcs`,
      `avif`,
      `bmp`,
      `cgm`,
      `dicom-rle`,
      `dpx`,
      `emf`,
      `fits`,
      `g3fax`,
      `gif`,
      `heic`,
      `heic-sequence`,
      `heif`,
      `heif-sequence`,
      `hej2k`,
      `ief`,
      `j2c`,
      `jaii`,
      `jais`,
      `jls`,
      `jp2`,
      `jpeg`,
      `jph`,
      `jphc`,
      `jpm`,
      `jpx`,
      `jxl`,
      `jxr`,
      `jxra`,
      `jxrs`,
      `jxs`,
      `jxsc`,
      `jxsi`,
      `jxss`,
      `ktx`,
      `ktx2`,
      `naplps`,
      `pjpeg`,
      `png`,
      `prs.btif`,
      `prs.pti`,
      `pwg-raster`,
      `sgi`,
      `svg+xml`,
      `t38`,
      `tiff`,
      `tiff-fx`,
      `vnd.adobe.photoshop`,
      `vnd.airzip.accelerator.azv`,
      `vnd.blockfact.facti`,
      `vnd.clip`,
      `vnd.cns.inf2`,
      `vnd.dece.graphic`,
      `vnd.djvu`,
      `vnd.dvb.subtitle`,
      `vnd.dwg`,
      `vnd.dxf`,
      `vnd.fastbidsheet`,
      `vnd.fpx`,
      `vnd.fst`,
      `vnd.fujixerox.edmics-mmr`,
      `vnd.fujixerox.edmics-rlc`,
      `vnd.globalgraphics.pgb`,
      `vnd.microsoft.icon`,
      `vnd.mix`,
      `vnd.mozilla.apng`,
      `vnd.ms-dds`,
      `vnd.ms-modi`,
      `vnd.ms-photo`,
      `vnd.net-fpx`,
      `vnd.pco.b16`,
      `vnd.radiance`,
      `vnd.sealed.png`,
      `vnd.sealedmedia.softseal.gif`,
      `vnd.sealedmedia.softseal.jpg`,
      `vnd.svf`,
      `vnd.tencent.tap`,
      `vnd.valve.source.texture`,
      `vnd.wap.wbmp`,
      `vnd.xiff`,
      `vnd.zbrush.pcx`,
      `webp`,
      `wmf`,
      `x-3ds`,
      `x-adobe-dng`,
      `x-cmu-raster`,
      `x-cmx`,
      `x-emf`,
      `x-freehand`,
      `x-icon`,
      `x-jng`,
      `x-mrsid-image`,
      `x-ms-bmp`,
      `x-pcx`,
      `x-pict`,
      `x-portable-anymap`,
      `x-portable-bitmap`,
      `x-portable-graymap`,
      `x-portable-pixmap`,
      `x-rgb`,
      `x-tga`,
      `x-wmf`,
      `x-xbitmap`,
      `x-xcf`,
      `x-xpixmap`,
      `x-xwindowdump`
    )
  }

  object message {
    lazy val `bhttp`: MediaType =
      MediaType("message", "bhttp", compressible = false, binary = true)

    lazy val `cpim`: MediaType =
      MediaType("message", "cpim", compressible = false, binary = true)

    lazy val `delivery-status`: MediaType =
      MediaType("message", "delivery-status", compressible = false, binary = true)

    lazy val `disposition-notification`: MediaType =
      MediaType("message", "disposition-notification", compressible = false, binary = true)

    lazy val `external-body`: MediaType =
      MediaType("message", "external-body", compressible = false, binary = true)

    lazy val `feedback-report`: MediaType =
      MediaType("message", "feedback-report", compressible = false, binary = true)

    lazy val `global`: MediaType =
      MediaType("message", "global", compressible = false, binary = true, fileExtensions = List("u8msg"))

    lazy val `global-delivery-status`: MediaType =
      MediaType(
        "message",
        "global-delivery-status",
        compressible = false,
        binary = true,
        fileExtensions = List("u8dsn")
      )

    lazy val `global-disposition-notification`: MediaType =
      MediaType(
        "message",
        "global-disposition-notification",
        compressible = false,
        binary = true,
        fileExtensions = List("u8mdn")
      )

    lazy val `global-headers`: MediaType =
      MediaType("message", "global-headers", compressible = false, binary = true, fileExtensions = List("u8hdr"))

    lazy val `http`: MediaType =
      MediaType("message", "http", compressible = false, binary = true)

    lazy val `imdn+xml`: MediaType =
      MediaType("message", "imdn+xml", compressible = true, binary = false)

    lazy val `mls`: MediaType =
      MediaType("message", "mls", compressible = false, binary = true)

    lazy val `news`: MediaType =
      MediaType("message", "news", compressible = false, binary = true)

    lazy val `ohttp-req`: MediaType =
      MediaType("message", "ohttp-req", compressible = false, binary = true)

    lazy val `ohttp-res`: MediaType =
      MediaType("message", "ohttp-res", compressible = false, binary = true)

    lazy val `partial`: MediaType =
      MediaType("message", "partial", compressible = false, binary = true)

    lazy val `rfc822`: MediaType =
      MediaType(
        "message",
        "rfc822",
        compressible = true,
        binary = false,
        fileExtensions = List("eml", "mime", "mht", "mhtml")
      )

    lazy val `s-http`: MediaType =
      MediaType("message", "s-http", compressible = false, binary = true)

    lazy val `sip`: MediaType =
      MediaType("message", "sip", compressible = false, binary = true)

    lazy val `sipfrag`: MediaType =
      MediaType("message", "sipfrag", compressible = false, binary = true)

    lazy val `tracking-status`: MediaType =
      MediaType("message", "tracking-status", compressible = false, binary = true)

    lazy val `vnd.si.simp`: MediaType =
      MediaType("message", "vnd.si.simp", compressible = false, binary = true)

    lazy val `vnd.wfa.wsc`: MediaType =
      MediaType("message", "vnd.wfa.wsc", compressible = false, binary = true, fileExtensions = List("wsc"))

    lazy val all: List[MediaType] = List(
      `bhttp`,
      `cpim`,
      `delivery-status`,
      `disposition-notification`,
      `external-body`,
      `feedback-report`,
      `global`,
      `global-delivery-status`,
      `global-disposition-notification`,
      `global-headers`,
      `http`,
      `imdn+xml`,
      `mls`,
      `news`,
      `ohttp-req`,
      `ohttp-res`,
      `partial`,
      `rfc822`,
      `s-http`,
      `sip`,
      `sipfrag`,
      `tracking-status`,
      `vnd.si.simp`,
      `vnd.wfa.wsc`
    )
  }

  object model {
    lazy val `3mf`: MediaType =
      MediaType("model", "3mf", compressible = false, binary = true, fileExtensions = List("3mf"))

    lazy val `e57`: MediaType =
      MediaType("model", "e57", compressible = false, binary = true)

    lazy val `gltf+json`: MediaType =
      MediaType("model", "gltf+json", compressible = true, binary = true, fileExtensions = List("gltf"))

    lazy val `gltf-binary`: MediaType =
      MediaType("model", "gltf-binary", compressible = true, binary = true, fileExtensions = List("glb"))

    lazy val `iges`: MediaType =
      MediaType("model", "iges", compressible = false, binary = true, fileExtensions = List("igs", "iges"))

    lazy val `jt`: MediaType =
      MediaType("model", "jt", compressible = false, binary = true, fileExtensions = List("jt"))

    lazy val `mesh`: MediaType =
      MediaType("model", "mesh", compressible = false, binary = true, fileExtensions = List("msh", "mesh", "silo"))

    lazy val `mtl`: MediaType =
      MediaType("model", "mtl", compressible = false, binary = true, fileExtensions = List("mtl"))

    lazy val `obj`: MediaType =
      MediaType("model", "obj", compressible = false, binary = true, fileExtensions = List("obj"))

    lazy val `prc`: MediaType =
      MediaType("model", "prc", compressible = false, binary = true, fileExtensions = List("prc"))

    lazy val `step`: MediaType =
      MediaType(
        "model",
        "step",
        compressible = false,
        binary = true,
        fileExtensions = List("step", "stp", "stpnc", "p21", "210")
      )

    lazy val `step+xml`: MediaType =
      MediaType("model", "step+xml", compressible = true, binary = true, fileExtensions = List("stpx"))

    lazy val `step+zip`: MediaType =
      MediaType("model", "step+zip", compressible = false, binary = true, fileExtensions = List("stpz"))

    lazy val `step-xml+zip`: MediaType =
      MediaType("model", "step-xml+zip", compressible = false, binary = true, fileExtensions = List("stpxz"))

    lazy val `stl`: MediaType =
      MediaType("model", "stl", compressible = false, binary = true, fileExtensions = List("stl"))

    lazy val `u3d`: MediaType =
      MediaType("model", "u3d", compressible = false, binary = true, fileExtensions = List("u3d"))

    lazy val `vnd.bary`: MediaType =
      MediaType("model", "vnd.bary", compressible = false, binary = true, fileExtensions = List("bary"))

    lazy val `vnd.cld`: MediaType =
      MediaType("model", "vnd.cld", compressible = false, binary = true, fileExtensions = List("cld"))

    lazy val `vnd.collada+xml`: MediaType =
      MediaType("model", "vnd.collada+xml", compressible = true, binary = true, fileExtensions = List("dae"))

    lazy val `vnd.dwf`: MediaType =
      MediaType("model", "vnd.dwf", compressible = false, binary = true, fileExtensions = List("dwf"))

    lazy val `vnd.flatland.3dml`: MediaType =
      MediaType("model", "vnd.flatland.3dml", compressible = false, binary = true)

    lazy val `vnd.gdl`: MediaType =
      MediaType("model", "vnd.gdl", compressible = false, binary = true, fileExtensions = List("gdl"))

    lazy val `vnd.gs-gdl`: MediaType =
      MediaType("model", "vnd.gs-gdl", compressible = false, binary = true)

    lazy val `vnd.gs.gdl`: MediaType =
      MediaType("model", "vnd.gs.gdl", compressible = false, binary = true)

    lazy val `vnd.gtw`: MediaType =
      MediaType("model", "vnd.gtw", compressible = false, binary = true, fileExtensions = List("gtw"))

    lazy val `vnd.moml+xml`: MediaType =
      MediaType("model", "vnd.moml+xml", compressible = true, binary = true)

    lazy val `vnd.mts`: MediaType =
      MediaType("model", "vnd.mts", compressible = false, binary = true, fileExtensions = List("mts"))

    lazy val `vnd.opengex`: MediaType =
      MediaType("model", "vnd.opengex", compressible = false, binary = true, fileExtensions = List("ogex"))

    lazy val `vnd.parasolid.transmit.binary`: MediaType =
      MediaType(
        "model",
        "vnd.parasolid.transmit.binary",
        compressible = false,
        binary = true,
        fileExtensions = List("x_b")
      )

    lazy val `vnd.parasolid.transmit.text`: MediaType =
      MediaType(
        "model",
        "vnd.parasolid.transmit.text",
        compressible = false,
        binary = true,
        fileExtensions = List("x_t")
      )

    lazy val `vnd.pytha.pyox`: MediaType =
      MediaType("model", "vnd.pytha.pyox", compressible = false, binary = true, fileExtensions = List("pyo", "pyox"))

    lazy val `vnd.rosette.annotated-data-model`: MediaType =
      MediaType("model", "vnd.rosette.annotated-data-model", compressible = false, binary = true)

    lazy val `vnd.sap.vds`: MediaType =
      MediaType("model", "vnd.sap.vds", compressible = false, binary = true, fileExtensions = List("vds"))

    lazy val `vnd.usda`: MediaType =
      MediaType("model", "vnd.usda", compressible = false, binary = true, fileExtensions = List("usda"))

    lazy val `vnd.usdz+zip`: MediaType =
      MediaType("model", "vnd.usdz+zip", compressible = false, binary = true, fileExtensions = List("usdz"))

    lazy val `vnd.valve.source.compiled-map`: MediaType =
      MediaType(
        "model",
        "vnd.valve.source.compiled-map",
        compressible = false,
        binary = true,
        fileExtensions = List("bsp")
      )

    lazy val `vnd.vtu`: MediaType =
      MediaType("model", "vnd.vtu", compressible = false, binary = true, fileExtensions = List("vtu"))

    lazy val `vrml`: MediaType =
      MediaType("model", "vrml", compressible = false, binary = true, fileExtensions = List("wrl", "vrml"))

    lazy val `x3d+binary`: MediaType =
      MediaType("model", "x3d+binary", compressible = false, binary = true, fileExtensions = List("x3db", "x3dbz"))

    lazy val `x3d+fastinfoset`: MediaType =
      MediaType("model", "x3d+fastinfoset", compressible = false, binary = true, fileExtensions = List("x3db"))

    lazy val `x3d+vrml`: MediaType =
      MediaType("model", "x3d+vrml", compressible = false, binary = true, fileExtensions = List("x3dv", "x3dvz"))

    lazy val `x3d+xml`: MediaType =
      MediaType("model", "x3d+xml", compressible = true, binary = true, fileExtensions = List("x3d", "x3dz"))

    lazy val `x3d-vrml`: MediaType =
      MediaType("model", "x3d-vrml", compressible = false, binary = true, fileExtensions = List("x3dv"))

    lazy val all: List[MediaType] = List(
      `3mf`,
      `e57`,
      `gltf+json`,
      `gltf-binary`,
      `iges`,
      `jt`,
      `mesh`,
      `mtl`,
      `obj`,
      `prc`,
      `step`,
      `step+xml`,
      `step+zip`,
      `step-xml+zip`,
      `stl`,
      `u3d`,
      `vnd.bary`,
      `vnd.cld`,
      `vnd.collada+xml`,
      `vnd.dwf`,
      `vnd.flatland.3dml`,
      `vnd.gdl`,
      `vnd.gs-gdl`,
      `vnd.gs.gdl`,
      `vnd.gtw`,
      `vnd.moml+xml`,
      `vnd.mts`,
      `vnd.opengex`,
      `vnd.parasolid.transmit.binary`,
      `vnd.parasolid.transmit.text`,
      `vnd.pytha.pyox`,
      `vnd.rosette.annotated-data-model`,
      `vnd.sap.vds`,
      `vnd.usda`,
      `vnd.usdz+zip`,
      `vnd.valve.source.compiled-map`,
      `vnd.vtu`,
      `vrml`,
      `x3d+binary`,
      `x3d+fastinfoset`,
      `x3d+vrml`,
      `x3d+xml`,
      `x3d-vrml`
    )
  }

  object multipart {
    lazy val `alternative`: MediaType =
      MediaType("multipart", "alternative", compressible = false, binary = true)

    lazy val `appledouble`: MediaType =
      MediaType("multipart", "appledouble", compressible = false, binary = true)

    lazy val `byteranges`: MediaType =
      MediaType("multipart", "byteranges", compressible = false, binary = true)

    lazy val `digest`: MediaType =
      MediaType("multipart", "digest", compressible = false, binary = true)

    lazy val `encrypted`: MediaType =
      MediaType("multipart", "encrypted", compressible = false, binary = true)

    lazy val `form-data`: MediaType =
      MediaType("multipart", "form-data", compressible = false, binary = true)

    lazy val `header-set`: MediaType =
      MediaType("multipart", "header-set", compressible = false, binary = true)

    lazy val `mixed`: MediaType =
      MediaType("multipart", "mixed", compressible = false, binary = true)

    lazy val `multilingual`: MediaType =
      MediaType("multipart", "multilingual", compressible = false, binary = true)

    lazy val `parallel`: MediaType =
      MediaType("multipart", "parallel", compressible = false, binary = true)

    lazy val `related`: MediaType =
      MediaType("multipart", "related", compressible = false, binary = true)

    lazy val `report`: MediaType =
      MediaType("multipart", "report", compressible = false, binary = true)

    lazy val `signed`: MediaType =
      MediaType("multipart", "signed", compressible = false, binary = true)

    lazy val `vnd.bint.med-plus`: MediaType =
      MediaType("multipart", "vnd.bint.med-plus", compressible = false, binary = true)

    lazy val `voice-message`: MediaType =
      MediaType("multipart", "voice-message", compressible = false, binary = true)

    lazy val `x-mixed-replace`: MediaType =
      MediaType("multipart", "x-mixed-replace", compressible = false, binary = true)

    lazy val all: List[MediaType] = List(
      `alternative`,
      `appledouble`,
      `byteranges`,
      `digest`,
      `encrypted`,
      `form-data`,
      `header-set`,
      `mixed`,
      `multilingual`,
      `parallel`,
      `related`,
      `report`,
      `signed`,
      `vnd.bint.med-plus`,
      `voice-message`,
      `x-mixed-replace`
    )
  }

  object text {
    lazy val `1d-interleaved-parityfec`: MediaType =
      MediaType("text", "1d-interleaved-parityfec", compressible = false, binary = false)

    lazy val `cache-manifest`: MediaType =
      MediaType(
        "text",
        "cache-manifest",
        compressible = true,
        binary = false,
        fileExtensions = List("appcache", "manifest")
      )

    lazy val `calendar`: MediaType =
      MediaType("text", "calendar", compressible = false, binary = false, fileExtensions = List("ics", "ifb"))

    lazy val `cmd`: MediaType =
      MediaType("text", "cmd", compressible = true, binary = false)

    lazy val `coffeescript`: MediaType =
      MediaType(
        "text",
        "coffeescript",
        compressible = false,
        binary = false,
        fileExtensions = List("coffee", "litcoffee")
      )

    lazy val `cql`: MediaType =
      MediaType("text", "cql", compressible = false, binary = false)

    lazy val `cql-expression`: MediaType =
      MediaType("text", "cql-expression", compressible = false, binary = false)

    lazy val `cql-identifier`: MediaType =
      MediaType("text", "cql-identifier", compressible = false, binary = false)

    lazy val `css`: MediaType =
      MediaType("text", "css", compressible = true, binary = false, fileExtensions = List("css"))

    lazy val `csv`: MediaType =
      MediaType("text", "csv", compressible = true, binary = false, fileExtensions = List("csv"))

    lazy val `csv-schema`: MediaType =
      MediaType("text", "csv-schema", compressible = false, binary = false)

    lazy val `directory`: MediaType =
      MediaType("text", "directory", compressible = false, binary = false)

    lazy val `dns`: MediaType =
      MediaType("text", "dns", compressible = false, binary = false)

    lazy val `ecmascript`: MediaType =
      MediaType("text", "ecmascript", compressible = false, binary = false)

    lazy val `encaprtp`: MediaType =
      MediaType("text", "encaprtp", compressible = false, binary = false)

    lazy val `enriched`: MediaType =
      MediaType("text", "enriched", compressible = false, binary = false)

    lazy val `fhirpath`: MediaType =
      MediaType("text", "fhirpath", compressible = false, binary = false)

    lazy val `flexfec`: MediaType =
      MediaType("text", "flexfec", compressible = false, binary = false)

    lazy val `fwdred`: MediaType =
      MediaType("text", "fwdred", compressible = false, binary = false)

    lazy val `gff3`: MediaType =
      MediaType("text", "gff3", compressible = false, binary = false)

    lazy val `grammar-ref-list`: MediaType =
      MediaType("text", "grammar-ref-list", compressible = false, binary = false)

    lazy val `hl7v2`: MediaType =
      MediaType("text", "hl7v2", compressible = false, binary = false)

    lazy val `html`: MediaType =
      MediaType("text", "html", compressible = true, binary = false, fileExtensions = List("html", "htm", "shtml"))

    lazy val `jade`: MediaType =
      MediaType("text", "jade", compressible = false, binary = false, fileExtensions = List("jade"))

    lazy val `javascript`: MediaType =
      MediaType("text", "javascript", compressible = true, binary = false, fileExtensions = List("js", "mjs"))

    lazy val `jcr-cnd`: MediaType =
      MediaType("text", "jcr-cnd", compressible = false, binary = false)

    lazy val `jsx`: MediaType =
      MediaType("text", "jsx", compressible = true, binary = false, fileExtensions = List("jsx"))

    lazy val `less`: MediaType =
      MediaType("text", "less", compressible = true, binary = false, fileExtensions = List("less"))

    lazy val `markdown`: MediaType =
      MediaType("text", "markdown", compressible = true, binary = false, fileExtensions = List("md", "markdown"))

    lazy val `mathml`: MediaType =
      MediaType("text", "mathml", compressible = false, binary = false, fileExtensions = List("mml"))

    lazy val `mdx`: MediaType =
      MediaType("text", "mdx", compressible = true, binary = false, fileExtensions = List("mdx"))

    lazy val `mizar`: MediaType =
      MediaType("text", "mizar", compressible = false, binary = false)

    lazy val `n3`: MediaType =
      MediaType("text", "n3", compressible = true, binary = false, fileExtensions = List("n3"))

    lazy val `org`: MediaType =
      MediaType("text", "org", compressible = false, binary = false)

    lazy val `parameters`: MediaType =
      MediaType("text", "parameters", compressible = false, binary = false)

    lazy val `parityfec`: MediaType =
      MediaType("text", "parityfec", compressible = false, binary = false)

    lazy val `plain`: MediaType =
      MediaType(
        "text",
        "plain",
        compressible = true,
        binary = false,
        fileExtensions = List("txt", "text", "conf", "def", "list", "log", "in", "ini")
      )

    lazy val `provenance-notation`: MediaType =
      MediaType("text", "provenance-notation", compressible = false, binary = false)

    lazy val `prs.fallenstein.rst`: MediaType =
      MediaType("text", "prs.fallenstein.rst", compressible = false, binary = false)

    lazy val `prs.lines.tag`: MediaType =
      MediaType("text", "prs.lines.tag", compressible = false, binary = false, fileExtensions = List("dsc"))

    lazy val `prs.prop.logic`: MediaType =
      MediaType("text", "prs.prop.logic", compressible = false, binary = false)

    lazy val `prs.texi`: MediaType =
      MediaType("text", "prs.texi", compressible = false, binary = false)

    lazy val `raptorfec`: MediaType =
      MediaType("text", "raptorfec", compressible = false, binary = false)

    lazy val `red`: MediaType =
      MediaType("text", "red", compressible = false, binary = false)

    lazy val `rfc822-headers`: MediaType =
      MediaType("text", "rfc822-headers", compressible = false, binary = false)

    lazy val `richtext`: MediaType =
      MediaType("text", "richtext", compressible = true, binary = false, fileExtensions = List("rtx"))

    lazy val `rtf`: MediaType =
      MediaType("text", "rtf", compressible = true, binary = false, fileExtensions = List("rtf"))

    lazy val `rtp-enc-aescm128`: MediaType =
      MediaType("text", "rtp-enc-aescm128", compressible = false, binary = false)

    lazy val `rtploopback`: MediaType =
      MediaType("text", "rtploopback", compressible = false, binary = false)

    lazy val `rtx`: MediaType =
      MediaType("text", "rtx", compressible = false, binary = false)

    lazy val `sgml`: MediaType =
      MediaType("text", "sgml", compressible = false, binary = false, fileExtensions = List("sgml", "sgm"))

    lazy val `shaclc`: MediaType =
      MediaType("text", "shaclc", compressible = false, binary = false)

    lazy val `shex`: MediaType =
      MediaType("text", "shex", compressible = false, binary = false, fileExtensions = List("shex"))

    lazy val `slim`: MediaType =
      MediaType("text", "slim", compressible = false, binary = false, fileExtensions = List("slim", "slm"))

    lazy val `spdx`: MediaType =
      MediaType("text", "spdx", compressible = false, binary = false, fileExtensions = List("spdx"))

    lazy val `strings`: MediaType =
      MediaType("text", "strings", compressible = false, binary = false)

    lazy val `stylus`: MediaType =
      MediaType("text", "stylus", compressible = false, binary = false, fileExtensions = List("stylus", "styl"))

    lazy val `t140`: MediaType =
      MediaType("text", "t140", compressible = false, binary = false)

    lazy val `tab-separated-values`: MediaType =
      MediaType("text", "tab-separated-values", compressible = true, binary = false, fileExtensions = List("tsv"))

    lazy val `troff`: MediaType =
      MediaType(
        "text",
        "troff",
        compressible = false,
        binary = false,
        fileExtensions = List("t", "tr", "roff", "man", "me", "ms")
      )

    lazy val `turtle`: MediaType =
      MediaType("text", "turtle", compressible = false, binary = false, fileExtensions = List("ttl"))

    lazy val `ulpfec`: MediaType =
      MediaType("text", "ulpfec", compressible = false, binary = false)

    lazy val `uri-list`: MediaType =
      MediaType("text", "uri-list", compressible = true, binary = false, fileExtensions = List("uri", "uris", "urls"))

    lazy val `vcard`: MediaType =
      MediaType("text", "vcard", compressible = true, binary = false, fileExtensions = List("vcard"))

    lazy val `vnd.a`: MediaType =
      MediaType("text", "vnd.a", compressible = false, binary = false)

    lazy val `vnd.abc`: MediaType =
      MediaType("text", "vnd.abc", compressible = false, binary = false)

    lazy val `vnd.ascii-art`: MediaType =
      MediaType("text", "vnd.ascii-art", compressible = false, binary = false)

    lazy val `vnd.curl`: MediaType =
      MediaType("text", "vnd.curl", compressible = false, binary = false, fileExtensions = List("curl"))

    lazy val `vnd.curl.dcurl`: MediaType =
      MediaType("text", "vnd.curl.dcurl", compressible = false, binary = false, fileExtensions = List("dcurl"))

    lazy val `vnd.curl.mcurl`: MediaType =
      MediaType("text", "vnd.curl.mcurl", compressible = false, binary = false, fileExtensions = List("mcurl"))

    lazy val `vnd.curl.scurl`: MediaType =
      MediaType("text", "vnd.curl.scurl", compressible = false, binary = false, fileExtensions = List("scurl"))

    lazy val `vnd.debian.copyright`: MediaType =
      MediaType("text", "vnd.debian.copyright", compressible = false, binary = false)

    lazy val `vnd.dmclientscript`: MediaType =
      MediaType("text", "vnd.dmclientscript", compressible = false, binary = false)

    lazy val `vnd.dvb.subtitle`: MediaType =
      MediaType("text", "vnd.dvb.subtitle", compressible = false, binary = false, fileExtensions = List("sub"))

    lazy val `vnd.esmertec.theme-descriptor`: MediaType =
      MediaType("text", "vnd.esmertec.theme-descriptor", compressible = false, binary = false)

    lazy val `vnd.exchangeable`: MediaType =
      MediaType("text", "vnd.exchangeable", compressible = false, binary = false)

    lazy val `vnd.familysearch.gedcom`: MediaType =
      MediaType("text", "vnd.familysearch.gedcom", compressible = false, binary = false, fileExtensions = List("ged"))

    lazy val `vnd.ficlab.flt`: MediaType =
      MediaType("text", "vnd.ficlab.flt", compressible = false, binary = false)

    lazy val `vnd.fly`: MediaType =
      MediaType("text", "vnd.fly", compressible = false, binary = false, fileExtensions = List("fly"))

    lazy val `vnd.fmi.flexstor`: MediaType =
      MediaType("text", "vnd.fmi.flexstor", compressible = false, binary = false, fileExtensions = List("flx"))

    lazy val `vnd.gml`: MediaType =
      MediaType("text", "vnd.gml", compressible = false, binary = false)

    lazy val `vnd.graphviz`: MediaType =
      MediaType("text", "vnd.graphviz", compressible = false, binary = false, fileExtensions = List("gv"))

    lazy val `vnd.hans`: MediaType =
      MediaType("text", "vnd.hans", compressible = false, binary = false)

    lazy val `vnd.hgl`: MediaType =
      MediaType("text", "vnd.hgl", compressible = false, binary = false)

    lazy val `vnd.in3d.3dml`: MediaType =
      MediaType("text", "vnd.in3d.3dml", compressible = false, binary = false, fileExtensions = List("3dml"))

    lazy val `vnd.in3d.spot`: MediaType =
      MediaType("text", "vnd.in3d.spot", compressible = false, binary = false, fileExtensions = List("spot"))

    lazy val `vnd.iptc.newsml`: MediaType =
      MediaType("text", "vnd.iptc.newsml", compressible = false, binary = false)

    lazy val `vnd.iptc.nitf`: MediaType =
      MediaType("text", "vnd.iptc.nitf", compressible = false, binary = false)

    lazy val `vnd.latex-z`: MediaType =
      MediaType("text", "vnd.latex-z", compressible = false, binary = false)

    lazy val `vnd.motorola.reflex`: MediaType =
      MediaType("text", "vnd.motorola.reflex", compressible = false, binary = false)

    lazy val `vnd.ms-mediapackage`: MediaType =
      MediaType("text", "vnd.ms-mediapackage", compressible = false, binary = false)

    lazy val `vnd.net2phone.commcenter.command`: MediaType =
      MediaType("text", "vnd.net2phone.commcenter.command", compressible = false, binary = false)

    lazy val `vnd.radisys.msml-basic-layout`: MediaType =
      MediaType("text", "vnd.radisys.msml-basic-layout", compressible = false, binary = false)

    lazy val `vnd.senx.warpscript`: MediaType =
      MediaType("text", "vnd.senx.warpscript", compressible = false, binary = false)

    lazy val `vnd.si.uricatalogue`: MediaType =
      MediaType("text", "vnd.si.uricatalogue", compressible = false, binary = false)

    lazy val `vnd.sosi`: MediaType =
      MediaType("text", "vnd.sosi", compressible = false, binary = false)

    lazy val `vnd.sun.j2me.app-descriptor`: MediaType =
      MediaType(
        "text",
        "vnd.sun.j2me.app-descriptor",
        compressible = false,
        binary = false,
        fileExtensions = List("jad")
      )

    lazy val `vnd.trolltech.linguist`: MediaType =
      MediaType("text", "vnd.trolltech.linguist", compressible = false, binary = false)

    lazy val `vnd.typst`: MediaType =
      MediaType("text", "vnd.typst", compressible = false, binary = false)

    lazy val `vnd.vcf`: MediaType =
      MediaType("text", "vnd.vcf", compressible = false, binary = false)

    lazy val `vnd.wap.si`: MediaType =
      MediaType("text", "vnd.wap.si", compressible = false, binary = false)

    lazy val `vnd.wap.sl`: MediaType =
      MediaType("text", "vnd.wap.sl", compressible = false, binary = false)

    lazy val `vnd.wap.wml`: MediaType =
      MediaType("text", "vnd.wap.wml", compressible = false, binary = false, fileExtensions = List("wml"))

    lazy val `vnd.wap.wmlscript`: MediaType =
      MediaType("text", "vnd.wap.wmlscript", compressible = false, binary = false, fileExtensions = List("wmls"))

    lazy val `vnd.zoo.kcl`: MediaType =
      MediaType("text", "vnd.zoo.kcl", compressible = false, binary = false)

    lazy val `vtt`: MediaType =
      MediaType("text", "vtt", compressible = true, binary = false, fileExtensions = List("vtt"))

    lazy val `wgsl`: MediaType =
      MediaType("text", "wgsl", compressible = false, binary = false, fileExtensions = List("wgsl"))

    lazy val `x-asm`: MediaType =
      MediaType("text", "x-asm", compressible = false, binary = false, fileExtensions = List("s", "asm"))

    lazy val `x-c`: MediaType =
      MediaType(
        "text",
        "x-c",
        compressible = false,
        binary = false,
        fileExtensions = List("c", "cc", "cxx", "cpp", "h", "hh", "dic")
      )

    lazy val `x-component`: MediaType =
      MediaType("text", "x-component", compressible = true, binary = false, fileExtensions = List("htc"))

    lazy val `x-fortran`: MediaType =
      MediaType(
        "text",
        "x-fortran",
        compressible = false,
        binary = false,
        fileExtensions = List("f", "for", "f77", "f90")
      )

    lazy val `x-gwt-rpc`: MediaType =
      MediaType("text", "x-gwt-rpc", compressible = true, binary = false)

    lazy val `x-handlebars-template`: MediaType =
      MediaType("text", "x-handlebars-template", compressible = false, binary = false, fileExtensions = List("hbs"))

    lazy val `x-java-source`: MediaType =
      MediaType("text", "x-java-source", compressible = false, binary = false, fileExtensions = List("java"))

    lazy val `x-jquery-tmpl`: MediaType =
      MediaType("text", "x-jquery-tmpl", compressible = true, binary = false)

    lazy val `x-lua`: MediaType =
      MediaType("text", "x-lua", compressible = false, binary = false, fileExtensions = List("lua"))

    lazy val `x-markdown`: MediaType =
      MediaType("text", "x-markdown", compressible = true, binary = false, fileExtensions = List("mkd"))

    lazy val `x-nfo`: MediaType =
      MediaType("text", "x-nfo", compressible = false, binary = false, fileExtensions = List("nfo"))

    lazy val `x-opml`: MediaType =
      MediaType("text", "x-opml", compressible = false, binary = false, fileExtensions = List("opml"))

    lazy val `x-org`: MediaType =
      MediaType("text", "x-org", compressible = true, binary = false, fileExtensions = List("org"))

    lazy val `x-pascal`: MediaType =
      MediaType("text", "x-pascal", compressible = false, binary = false, fileExtensions = List("p", "pas"))

    lazy val `x-php`: MediaType =
      MediaType("text", "x-php", compressible = true, binary = false, fileExtensions = List("php"))

    lazy val `x-processing`: MediaType =
      MediaType("text", "x-processing", compressible = true, binary = false, fileExtensions = List("pde"))

    lazy val `x-sass`: MediaType =
      MediaType("text", "x-sass", compressible = false, binary = false, fileExtensions = List("sass"))

    lazy val `x-scss`: MediaType =
      MediaType("text", "x-scss", compressible = false, binary = false, fileExtensions = List("scss"))

    lazy val `x-setext`: MediaType =
      MediaType("text", "x-setext", compressible = false, binary = false, fileExtensions = List("etx"))

    lazy val `x-sfv`: MediaType =
      MediaType("text", "x-sfv", compressible = false, binary = false, fileExtensions = List("sfv"))

    lazy val `x-suse-ymp`: MediaType =
      MediaType("text", "x-suse-ymp", compressible = true, binary = false, fileExtensions = List("ymp"))

    lazy val `x-uuencode`: MediaType =
      MediaType("text", "x-uuencode", compressible = false, binary = false, fileExtensions = List("uu"))

    lazy val `x-vcalendar`: MediaType =
      MediaType("text", "x-vcalendar", compressible = false, binary = false, fileExtensions = List("vcs"))

    lazy val `x-vcard`: MediaType =
      MediaType("text", "x-vcard", compressible = false, binary = false, fileExtensions = List("vcf"))

    lazy val `xml`: MediaType =
      MediaType("text", "xml", compressible = true, binary = false, fileExtensions = List("xml"))

    lazy val `xml-external-parsed-entity`: MediaType =
      MediaType("text", "xml-external-parsed-entity", compressible = false, binary = false)

    lazy val `yaml`: MediaType =
      MediaType("text", "yaml", compressible = true, binary = false, fileExtensions = List("yaml", "yml"))

    lazy val all: List[MediaType] = List(
      `1d-interleaved-parityfec`,
      `cache-manifest`,
      `calendar`,
      `cmd`,
      `coffeescript`,
      `cql`,
      `cql-expression`,
      `cql-identifier`,
      `css`,
      `csv`,
      `csv-schema`,
      `directory`,
      `dns`,
      `ecmascript`,
      `encaprtp`,
      `enriched`,
      `fhirpath`,
      `flexfec`,
      `fwdred`,
      `gff3`,
      `grammar-ref-list`,
      `hl7v2`,
      `html`,
      `jade`,
      `javascript`,
      `jcr-cnd`,
      `jsx`,
      `less`,
      `markdown`,
      `mathml`,
      `mdx`,
      `mizar`,
      `n3`,
      `org`,
      `parameters`,
      `parityfec`,
      `plain`,
      `provenance-notation`,
      `prs.fallenstein.rst`,
      `prs.lines.tag`,
      `prs.prop.logic`,
      `prs.texi`,
      `raptorfec`,
      `red`,
      `rfc822-headers`,
      `richtext`,
      `rtf`,
      `rtp-enc-aescm128`,
      `rtploopback`,
      `rtx`,
      `sgml`,
      `shaclc`,
      `shex`,
      `slim`,
      `spdx`,
      `strings`,
      `stylus`,
      `t140`,
      `tab-separated-values`,
      `troff`,
      `turtle`,
      `ulpfec`,
      `uri-list`,
      `vcard`,
      `vnd.a`,
      `vnd.abc`,
      `vnd.ascii-art`,
      `vnd.curl`,
      `vnd.curl.dcurl`,
      `vnd.curl.mcurl`,
      `vnd.curl.scurl`,
      `vnd.debian.copyright`,
      `vnd.dmclientscript`,
      `vnd.dvb.subtitle`,
      `vnd.esmertec.theme-descriptor`,
      `vnd.exchangeable`,
      `vnd.familysearch.gedcom`,
      `vnd.ficlab.flt`,
      `vnd.fly`,
      `vnd.fmi.flexstor`,
      `vnd.gml`,
      `vnd.graphviz`,
      `vnd.hans`,
      `vnd.hgl`,
      `vnd.in3d.3dml`,
      `vnd.in3d.spot`,
      `vnd.iptc.newsml`,
      `vnd.iptc.nitf`,
      `vnd.latex-z`,
      `vnd.motorola.reflex`,
      `vnd.ms-mediapackage`,
      `vnd.net2phone.commcenter.command`,
      `vnd.radisys.msml-basic-layout`,
      `vnd.senx.warpscript`,
      `vnd.si.uricatalogue`,
      `vnd.sosi`,
      `vnd.sun.j2me.app-descriptor`,
      `vnd.trolltech.linguist`,
      `vnd.typst`,
      `vnd.vcf`,
      `vnd.wap.si`,
      `vnd.wap.sl`,
      `vnd.wap.wml`,
      `vnd.wap.wmlscript`,
      `vnd.zoo.kcl`,
      `vtt`,
      `wgsl`,
      `x-asm`,
      `x-c`,
      `x-component`,
      `x-fortran`,
      `x-gwt-rpc`,
      `x-handlebars-template`,
      `x-java-source`,
      `x-jquery-tmpl`,
      `x-lua`,
      `x-markdown`,
      `x-nfo`,
      `x-opml`,
      `x-org`,
      `x-pascal`,
      `x-php`,
      `x-processing`,
      `x-sass`,
      `x-scss`,
      `x-setext`,
      `x-sfv`,
      `x-suse-ymp`,
      `x-uuencode`,
      `x-vcalendar`,
      `x-vcard`,
      `xml`,
      `xml-external-parsed-entity`,
      `yaml`
    )
  }

  object video {
    lazy val `1d-interleaved-parityfec`: MediaType =
      MediaType("video", "1d-interleaved-parityfec", compressible = false, binary = true)

    lazy val `3gpp`: MediaType =
      MediaType("video", "3gpp", compressible = false, binary = true, fileExtensions = List("3gp", "3gpp"))

    lazy val `3gpp-tt`: MediaType =
      MediaType("video", "3gpp-tt", compressible = false, binary = true)

    lazy val `3gpp2`: MediaType =
      MediaType("video", "3gpp2", compressible = false, binary = true, fileExtensions = List("3g2"))

    lazy val `av1`: MediaType =
      MediaType("video", "av1", compressible = false, binary = true)

    lazy val `bmpeg`: MediaType =
      MediaType("video", "bmpeg", compressible = false, binary = true)

    lazy val `bt656`: MediaType =
      MediaType("video", "bt656", compressible = false, binary = true)

    lazy val `celb`: MediaType =
      MediaType("video", "celb", compressible = false, binary = true)

    lazy val `dv`: MediaType =
      MediaType("video", "dv", compressible = false, binary = true)

    lazy val `encaprtp`: MediaType =
      MediaType("video", "encaprtp", compressible = false, binary = true)

    lazy val `evc`: MediaType =
      MediaType("video", "evc", compressible = false, binary = true)

    lazy val `ffv1`: MediaType =
      MediaType("video", "ffv1", compressible = false, binary = true)

    lazy val `flexfec`: MediaType =
      MediaType("video", "flexfec", compressible = false, binary = true)

    lazy val `h261`: MediaType =
      MediaType("video", "h261", compressible = false, binary = true, fileExtensions = List("h261"))

    lazy val `h263`: MediaType =
      MediaType("video", "h263", compressible = false, binary = true, fileExtensions = List("h263"))

    lazy val `h263-1998`: MediaType =
      MediaType("video", "h263-1998", compressible = false, binary = true)

    lazy val `h263-2000`: MediaType =
      MediaType("video", "h263-2000", compressible = false, binary = true)

    lazy val `h264`: MediaType =
      MediaType("video", "h264", compressible = false, binary = true, fileExtensions = List("h264"))

    lazy val `h264-rcdo`: MediaType =
      MediaType("video", "h264-rcdo", compressible = false, binary = true)

    lazy val `h264-svc`: MediaType =
      MediaType("video", "h264-svc", compressible = false, binary = true)

    lazy val `h265`: MediaType =
      MediaType("video", "h265", compressible = false, binary = true)

    lazy val `h266`: MediaType =
      MediaType("video", "h266", compressible = false, binary = true)

    lazy val `iso.segment`: MediaType =
      MediaType("video", "iso.segment", compressible = false, binary = true, fileExtensions = List("m4s"))

    lazy val `jpeg`: MediaType =
      MediaType("video", "jpeg", compressible = false, binary = true, fileExtensions = List("jpgv"))

    lazy val `jpeg2000`: MediaType =
      MediaType("video", "jpeg2000", compressible = false, binary = true)

    lazy val `jpeg2000-scl`: MediaType =
      MediaType("video", "jpeg2000-scl", compressible = false, binary = true)

    lazy val `jpm`: MediaType =
      MediaType("video", "jpm", compressible = false, binary = true, fileExtensions = List("jpm", "jpgm"))

    lazy val `jxsv`: MediaType =
      MediaType("video", "jxsv", compressible = false, binary = true)

    lazy val `lottie+json`: MediaType =
      MediaType("video", "lottie+json", compressible = true, binary = true)

    lazy val `matroska`: MediaType =
      MediaType("video", "matroska", compressible = false, binary = true, fileExtensions = List("mkv"))

    lazy val `matroska-3d`: MediaType =
      MediaType("video", "matroska-3d", compressible = false, binary = true, fileExtensions = List("mk3d"))

    lazy val `mj2`: MediaType =
      MediaType("video", "mj2", compressible = false, binary = true, fileExtensions = List("mj2", "mjp2"))

    lazy val `mp1s`: MediaType =
      MediaType("video", "mp1s", compressible = false, binary = true)

    lazy val `mp2p`: MediaType =
      MediaType("video", "mp2p", compressible = false, binary = true)

    lazy val `mp2t`: MediaType =
      MediaType("video", "mp2t", compressible = false, binary = true, fileExtensions = List("ts", "m2t", "m2ts", "mts"))

    lazy val `mp4`: MediaType =
      MediaType("video", "mp4", compressible = false, binary = true, fileExtensions = List("mp4", "mp4v", "mpg4"))

    lazy val `mp4v-es`: MediaType =
      MediaType("video", "mp4v-es", compressible = false, binary = true)

    lazy val `mpeg`: MediaType =
      MediaType(
        "video",
        "mpeg",
        compressible = false,
        binary = true,
        fileExtensions = List("mpeg", "mpg", "mpe", "m1v", "m2v")
      )

    lazy val `mpeg4-generic`: MediaType =
      MediaType("video", "mpeg4-generic", compressible = false, binary = true)

    lazy val `mpv`: MediaType =
      MediaType("video", "mpv", compressible = false, binary = true)

    lazy val `nv`: MediaType =
      MediaType("video", "nv", compressible = false, binary = true)

    lazy val `ogg`: MediaType =
      MediaType("video", "ogg", compressible = false, binary = true, fileExtensions = List("ogv"))

    lazy val `parityfec`: MediaType =
      MediaType("video", "parityfec", compressible = false, binary = true)

    lazy val `pointer`: MediaType =
      MediaType("video", "pointer", compressible = false, binary = true)

    lazy val `quicktime`: MediaType =
      MediaType("video", "quicktime", compressible = false, binary = true, fileExtensions = List("qt", "mov"))

    lazy val `raptorfec`: MediaType =
      MediaType("video", "raptorfec", compressible = false, binary = true)

    lazy val `raw`: MediaType =
      MediaType("video", "raw", compressible = false, binary = true)

    lazy val `rtp-enc-aescm128`: MediaType =
      MediaType("video", "rtp-enc-aescm128", compressible = false, binary = true)

    lazy val `rtploopback`: MediaType =
      MediaType("video", "rtploopback", compressible = false, binary = true)

    lazy val `rtx`: MediaType =
      MediaType("video", "rtx", compressible = false, binary = true)

    lazy val `scip`: MediaType =
      MediaType("video", "scip", compressible = false, binary = true)

    lazy val `smpte291`: MediaType =
      MediaType("video", "smpte291", compressible = false, binary = true)

    lazy val `smpte292m`: MediaType =
      MediaType("video", "smpte292m", compressible = false, binary = true)

    lazy val `ulpfec`: MediaType =
      MediaType("video", "ulpfec", compressible = false, binary = true)

    lazy val `vc1`: MediaType =
      MediaType("video", "vc1", compressible = false, binary = true)

    lazy val `vc2`: MediaType =
      MediaType("video", "vc2", compressible = false, binary = true)

    lazy val `vnd.blockfact.factv`: MediaType =
      MediaType("video", "vnd.blockfact.factv", compressible = false, binary = true)

    lazy val `vnd.cctv`: MediaType =
      MediaType("video", "vnd.cctv", compressible = false, binary = true)

    lazy val `vnd.dece.hd`: MediaType =
      MediaType("video", "vnd.dece.hd", compressible = false, binary = true, fileExtensions = List("uvh", "uvvh"))

    lazy val `vnd.dece.mobile`: MediaType =
      MediaType("video", "vnd.dece.mobile", compressible = false, binary = true, fileExtensions = List("uvm", "uvvm"))

    lazy val `vnd.dece.mp4`: MediaType =
      MediaType("video", "vnd.dece.mp4", compressible = false, binary = true)

    lazy val `vnd.dece.pd`: MediaType =
      MediaType("video", "vnd.dece.pd", compressible = false, binary = true, fileExtensions = List("uvp", "uvvp"))

    lazy val `vnd.dece.sd`: MediaType =
      MediaType("video", "vnd.dece.sd", compressible = false, binary = true, fileExtensions = List("uvs", "uvvs"))

    lazy val `vnd.dece.video`: MediaType =
      MediaType("video", "vnd.dece.video", compressible = false, binary = true, fileExtensions = List("uvv", "uvvv"))

    lazy val `vnd.directv.mpeg`: MediaType =
      MediaType("video", "vnd.directv.mpeg", compressible = false, binary = true)

    lazy val `vnd.directv.mpeg-tts`: MediaType =
      MediaType("video", "vnd.directv.mpeg-tts", compressible = false, binary = true)

    lazy val `vnd.dlna.mpeg-tts`: MediaType =
      MediaType("video", "vnd.dlna.mpeg-tts", compressible = false, binary = true)

    lazy val `vnd.dvb.file`: MediaType =
      MediaType("video", "vnd.dvb.file", compressible = false, binary = true, fileExtensions = List("dvb"))

    lazy val `vnd.fvt`: MediaType =
      MediaType("video", "vnd.fvt", compressible = false, binary = true, fileExtensions = List("fvt"))

    lazy val `vnd.hns.video`: MediaType =
      MediaType("video", "vnd.hns.video", compressible = false, binary = true)

    lazy val `vnd.iptvforum.1dparityfec-1010`: MediaType =
      MediaType("video", "vnd.iptvforum.1dparityfec-1010", compressible = false, binary = true)

    lazy val `vnd.iptvforum.1dparityfec-2005`: MediaType =
      MediaType("video", "vnd.iptvforum.1dparityfec-2005", compressible = false, binary = true)

    lazy val `vnd.iptvforum.2dparityfec-1010`: MediaType =
      MediaType("video", "vnd.iptvforum.2dparityfec-1010", compressible = false, binary = true)

    lazy val `vnd.iptvforum.2dparityfec-2005`: MediaType =
      MediaType("video", "vnd.iptvforum.2dparityfec-2005", compressible = false, binary = true)

    lazy val `vnd.iptvforum.ttsavc`: MediaType =
      MediaType("video", "vnd.iptvforum.ttsavc", compressible = false, binary = true)

    lazy val `vnd.iptvforum.ttsmpeg2`: MediaType =
      MediaType("video", "vnd.iptvforum.ttsmpeg2", compressible = false, binary = true)

    lazy val `vnd.motorola.video`: MediaType =
      MediaType("video", "vnd.motorola.video", compressible = false, binary = true)

    lazy val `vnd.motorola.videop`: MediaType =
      MediaType("video", "vnd.motorola.videop", compressible = false, binary = true)

    lazy val `vnd.mpegurl`: MediaType =
      MediaType("video", "vnd.mpegurl", compressible = false, binary = true, fileExtensions = List("mxu", "m4u"))

    lazy val `vnd.ms-playready.media.pyv`: MediaType =
      MediaType(
        "video",
        "vnd.ms-playready.media.pyv",
        compressible = false,
        binary = true,
        fileExtensions = List("pyv")
      )

    lazy val `vnd.nokia.interleaved-multimedia`: MediaType =
      MediaType("video", "vnd.nokia.interleaved-multimedia", compressible = false, binary = true)

    lazy val `vnd.nokia.mp4vr`: MediaType =
      MediaType("video", "vnd.nokia.mp4vr", compressible = false, binary = true)

    lazy val `vnd.nokia.videovoip`: MediaType =
      MediaType("video", "vnd.nokia.videovoip", compressible = false, binary = true)

    lazy val `vnd.objectvideo`: MediaType =
      MediaType("video", "vnd.objectvideo", compressible = false, binary = true)

    lazy val `vnd.planar`: MediaType =
      MediaType("video", "vnd.planar", compressible = false, binary = true)

    lazy val `vnd.radgamettools.bink`: MediaType =
      MediaType("video", "vnd.radgamettools.bink", compressible = false, binary = true)

    lazy val `vnd.radgamettools.smacker`: MediaType =
      MediaType("video", "vnd.radgamettools.smacker", compressible = false, binary = true)

    lazy val `vnd.sealed.mpeg1`: MediaType =
      MediaType("video", "vnd.sealed.mpeg1", compressible = false, binary = true)

    lazy val `vnd.sealed.mpeg4`: MediaType =
      MediaType("video", "vnd.sealed.mpeg4", compressible = false, binary = true)

    lazy val `vnd.sealed.swf`: MediaType =
      MediaType("video", "vnd.sealed.swf", compressible = false, binary = true)

    lazy val `vnd.sealedmedia.softseal.mov`: MediaType =
      MediaType("video", "vnd.sealedmedia.softseal.mov", compressible = false, binary = true)

    lazy val `vnd.uvvu.mp4`: MediaType =
      MediaType("video", "vnd.uvvu.mp4", compressible = false, binary = true, fileExtensions = List("uvu", "uvvu"))

    lazy val `vnd.vivo`: MediaType =
      MediaType("video", "vnd.vivo", compressible = false, binary = true, fileExtensions = List("viv"))

    lazy val `vnd.youtube.yt`: MediaType =
      MediaType("video", "vnd.youtube.yt", compressible = false, binary = true)

    lazy val `vp8`: MediaType =
      MediaType("video", "vp8", compressible = false, binary = true)

    lazy val `vp9`: MediaType =
      MediaType("video", "vp9", compressible = false, binary = true)

    lazy val `webm`: MediaType =
      MediaType("video", "webm", compressible = false, binary = true, fileExtensions = List("webm"))

    lazy val `x-f4v`: MediaType =
      MediaType("video", "x-f4v", compressible = false, binary = true, fileExtensions = List("f4v"))

    lazy val `x-fli`: MediaType =
      MediaType("video", "x-fli", compressible = false, binary = true, fileExtensions = List("fli"))

    lazy val `x-flv`: MediaType =
      MediaType("video", "x-flv", compressible = false, binary = true, fileExtensions = List("flv"))

    lazy val `x-m4v`: MediaType =
      MediaType("video", "x-m4v", compressible = false, binary = true, fileExtensions = List("m4v"))

    lazy val `x-matroska`: MediaType =
      MediaType("video", "x-matroska", compressible = false, binary = true, fileExtensions = List("mkv", "mk3d", "mks"))

    lazy val `x-mng`: MediaType =
      MediaType("video", "x-mng", compressible = false, binary = true, fileExtensions = List("mng"))

    lazy val `x-ms-asf`: MediaType =
      MediaType("video", "x-ms-asf", compressible = false, binary = true, fileExtensions = List("asf", "asx"))

    lazy val `x-ms-vob`: MediaType =
      MediaType("video", "x-ms-vob", compressible = false, binary = true, fileExtensions = List("vob"))

    lazy val `x-ms-wm`: MediaType =
      MediaType("video", "x-ms-wm", compressible = false, binary = true, fileExtensions = List("wm"))

    lazy val `x-ms-wmv`: MediaType =
      MediaType("video", "x-ms-wmv", compressible = false, binary = true, fileExtensions = List("wmv"))

    lazy val `x-ms-wmx`: MediaType =
      MediaType("video", "x-ms-wmx", compressible = false, binary = true, fileExtensions = List("wmx"))

    lazy val `x-ms-wvx`: MediaType =
      MediaType("video", "x-ms-wvx", compressible = false, binary = true, fileExtensions = List("wvx"))

    lazy val `x-msvideo`: MediaType =
      MediaType("video", "x-msvideo", compressible = false, binary = true, fileExtensions = List("avi"))

    lazy val `x-sgi-movie`: MediaType =
      MediaType("video", "x-sgi-movie", compressible = false, binary = true, fileExtensions = List("movie"))

    lazy val `x-smv`: MediaType =
      MediaType("video", "x-smv", compressible = false, binary = true, fileExtensions = List("smv"))

    lazy val all: List[MediaType] = List(
      `1d-interleaved-parityfec`,
      `3gpp`,
      `3gpp-tt`,
      `3gpp2`,
      `av1`,
      `bmpeg`,
      `bt656`,
      `celb`,
      `dv`,
      `encaprtp`,
      `evc`,
      `ffv1`,
      `flexfec`,
      `h261`,
      `h263`,
      `h263-1998`,
      `h263-2000`,
      `h264`,
      `h264-rcdo`,
      `h264-svc`,
      `h265`,
      `h266`,
      `iso.segment`,
      `jpeg`,
      `jpeg2000`,
      `jpeg2000-scl`,
      `jpm`,
      `jxsv`,
      `lottie+json`,
      `matroska`,
      `matroska-3d`,
      `mj2`,
      `mp1s`,
      `mp2p`,
      `mp2t`,
      `mp4`,
      `mp4v-es`,
      `mpeg`,
      `mpeg4-generic`,
      `mpv`,
      `nv`,
      `ogg`,
      `parityfec`,
      `pointer`,
      `quicktime`,
      `raptorfec`,
      `raw`,
      `rtp-enc-aescm128`,
      `rtploopback`,
      `rtx`,
      `scip`,
      `smpte291`,
      `smpte292m`,
      `ulpfec`,
      `vc1`,
      `vc2`,
      `vnd.blockfact.factv`,
      `vnd.cctv`,
      `vnd.dece.hd`,
      `vnd.dece.mobile`,
      `vnd.dece.mp4`,
      `vnd.dece.pd`,
      `vnd.dece.sd`,
      `vnd.dece.video`,
      `vnd.directv.mpeg`,
      `vnd.directv.mpeg-tts`,
      `vnd.dlna.mpeg-tts`,
      `vnd.dvb.file`,
      `vnd.fvt`,
      `vnd.hns.video`,
      `vnd.iptvforum.1dparityfec-1010`,
      `vnd.iptvforum.1dparityfec-2005`,
      `vnd.iptvforum.2dparityfec-1010`,
      `vnd.iptvforum.2dparityfec-2005`,
      `vnd.iptvforum.ttsavc`,
      `vnd.iptvforum.ttsmpeg2`,
      `vnd.motorola.video`,
      `vnd.motorola.videop`,
      `vnd.mpegurl`,
      `vnd.ms-playready.media.pyv`,
      `vnd.nokia.interleaved-multimedia`,
      `vnd.nokia.mp4vr`,
      `vnd.nokia.videovoip`,
      `vnd.objectvideo`,
      `vnd.planar`,
      `vnd.radgamettools.bink`,
      `vnd.radgamettools.smacker`,
      `vnd.sealed.mpeg1`,
      `vnd.sealed.mpeg4`,
      `vnd.sealed.swf`,
      `vnd.sealedmedia.softseal.mov`,
      `vnd.uvvu.mp4`,
      `vnd.vivo`,
      `vnd.youtube.yt`,
      `vp8`,
      `vp9`,
      `webm`,
      `x-f4v`,
      `x-fli`,
      `x-flv`,
      `x-m4v`,
      `x-matroska`,
      `x-mng`,
      `x-ms-asf`,
      `x-ms-vob`,
      `x-ms-wm`,
      `x-ms-wmv`,
      `x-ms-wmx`,
      `x-ms-wvx`,
      `x-msvideo`,
      `x-sgi-movie`,
      `x-smv`
    )
  }

  object x_conference {
    lazy val `x-cooltalk`: MediaType =
      MediaType("x-conference", "x-cooltalk", compressible = false, binary = true, fileExtensions = List("ice"))

    lazy val all: List[MediaType] = List(
      `x-cooltalk`
    )
  }

  object x_shader {
    lazy val `x-fragment`: MediaType =
      MediaType("x-shader", "x-fragment", compressible = true, binary = false)

    lazy val `x-vertex`: MediaType =
      MediaType("x-shader", "x-vertex", compressible = true, binary = false)

    lazy val all: List[MediaType] = List(
      `x-fragment`,
      `x-vertex`
    )
  }

  lazy val allMediaTypes: List[MediaType] =
    application.all ++ audio.all ++ chemical.all ++ font.all ++ image.all ++ message.all ++ model.all ++ multipart.all ++ text.all ++ video.all ++ x_conference.all ++ x_shader.all
}
