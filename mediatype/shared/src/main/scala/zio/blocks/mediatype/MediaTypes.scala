/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.mediatype

// AUTO-GENERATED - DO NOT EDIT
// Generated from https://github.com/jshttp/mime-db
// Run: sbt generateMediaTypes

object MediaTypes {
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

    lazy val a2l: MediaType =
      MediaType("application", "a2l", compressible = false, binary = true)

    lazy val aasZip: MediaType =
      MediaType("application", "aas+zip", compressible = false, binary = true)
    
    lazy val `aas+zip`: MediaType = aasZip

    lazy val aceCbor: MediaType =
      MediaType("application", "ace+cbor", compressible = false, binary = true)
    
    lazy val `ace+cbor`: MediaType = aceCbor

    lazy val aceJson: MediaType =
      MediaType("application", "ace+json", compressible = true, binary = false)
    
    lazy val `ace+json`: MediaType = aceJson

    lazy val aceGroupcommCbor: MediaType =
      MediaType("application", "ace-groupcomm+cbor", compressible = false, binary = true)
    
    lazy val `ace-groupcomm+cbor`: MediaType = aceGroupcommCbor

    lazy val aceTrlCbor: MediaType =
      MediaType("application", "ace-trl+cbor", compressible = false, binary = true)
    
    lazy val `ace-trl+cbor`: MediaType = aceTrlCbor

    lazy val activemessage: MediaType =
      MediaType("application", "activemessage", compressible = false, binary = true)

    lazy val activityJson: MediaType =
      MediaType("application", "activity+json", compressible = true, binary = false)
    
    lazy val `activity+json`: MediaType = activityJson

    lazy val aifCbor: MediaType =
      MediaType("application", "aif+cbor", compressible = false, binary = true)
    
    lazy val `aif+cbor`: MediaType = aifCbor

    lazy val aifJson: MediaType =
      MediaType("application", "aif+json", compressible = true, binary = false)
    
    lazy val `aif+json`: MediaType = aifJson

    lazy val altoCdniJson: MediaType =
      MediaType("application", "alto-cdni+json", compressible = true, binary = false)
    
    lazy val `alto-cdni+json`: MediaType = altoCdniJson

    lazy val altoCdnifilterJson: MediaType =
      MediaType("application", "alto-cdnifilter+json", compressible = true, binary = false)
    
    lazy val `alto-cdnifilter+json`: MediaType = altoCdnifilterJson

    lazy val altoCostmapJson: MediaType =
      MediaType("application", "alto-costmap+json", compressible = true, binary = false)
    
    lazy val `alto-costmap+json`: MediaType = altoCostmapJson

    lazy val altoCostmapfilterJson: MediaType =
      MediaType("application", "alto-costmapfilter+json", compressible = true, binary = false)
    
    lazy val `alto-costmapfilter+json`: MediaType = altoCostmapfilterJson

    lazy val altoDirectoryJson: MediaType =
      MediaType("application", "alto-directory+json", compressible = true, binary = false)
    
    lazy val `alto-directory+json`: MediaType = altoDirectoryJson

    lazy val altoEndpointcostJson: MediaType =
      MediaType("application", "alto-endpointcost+json", compressible = true, binary = false)
    
    lazy val `alto-endpointcost+json`: MediaType = altoEndpointcostJson

    lazy val altoEndpointcostparamsJson: MediaType =
      MediaType("application", "alto-endpointcostparams+json", compressible = true, binary = false)
    
    lazy val `alto-endpointcostparams+json`: MediaType = altoEndpointcostparamsJson

    lazy val altoEndpointpropJson: MediaType =
      MediaType("application", "alto-endpointprop+json", compressible = true, binary = false)
    
    lazy val `alto-endpointprop+json`: MediaType = altoEndpointpropJson

    lazy val altoEndpointpropparamsJson: MediaType =
      MediaType("application", "alto-endpointpropparams+json", compressible = true, binary = false)
    
    lazy val `alto-endpointpropparams+json`: MediaType = altoEndpointpropparamsJson

    lazy val altoErrorJson: MediaType =
      MediaType("application", "alto-error+json", compressible = true, binary = false)
    
    lazy val `alto-error+json`: MediaType = altoErrorJson

    lazy val altoNetworkmapJson: MediaType =
      MediaType("application", "alto-networkmap+json", compressible = true, binary = false)
    
    lazy val `alto-networkmap+json`: MediaType = altoNetworkmapJson

    lazy val altoNetworkmapfilterJson: MediaType =
      MediaType("application", "alto-networkmapfilter+json", compressible = true, binary = false)
    
    lazy val `alto-networkmapfilter+json`: MediaType = altoNetworkmapfilterJson

    lazy val altoPropmapJson: MediaType =
      MediaType("application", "alto-propmap+json", compressible = true, binary = false)
    
    lazy val `alto-propmap+json`: MediaType = altoPropmapJson

    lazy val altoPropmapparamsJson: MediaType =
      MediaType("application", "alto-propmapparams+json", compressible = true, binary = false)
    
    lazy val `alto-propmapparams+json`: MediaType = altoPropmapparamsJson

    lazy val altoTipsJson: MediaType =
      MediaType("application", "alto-tips+json", compressible = true, binary = false)
    
    lazy val `alto-tips+json`: MediaType = altoTipsJson

    lazy val altoTipsparamsJson: MediaType =
      MediaType("application", "alto-tipsparams+json", compressible = true, binary = false)
    
    lazy val `alto-tipsparams+json`: MediaType = altoTipsparamsJson

    lazy val altoUpdatestreamcontrolJson: MediaType =
      MediaType("application", "alto-updatestreamcontrol+json", compressible = true, binary = false)
    
    lazy val `alto-updatestreamcontrol+json`: MediaType = altoUpdatestreamcontrolJson

    lazy val altoUpdatestreamparamsJson: MediaType =
      MediaType("application", "alto-updatestreamparams+json", compressible = true, binary = false)
    
    lazy val `alto-updatestreamparams+json`: MediaType = altoUpdatestreamparamsJson

    lazy val aml: MediaType =
      MediaType("application", "aml", compressible = false, binary = true)

    lazy val andrewInset: MediaType =
      MediaType("application", "andrew-inset", compressible = false, binary = true, fileExtensions = List("ez"))
    
    lazy val `andrew-inset`: MediaType = andrewInset

    lazy val appinstaller: MediaType =
      MediaType("application", "appinstaller", compressible = false, binary = true, fileExtensions = List("appinstaller"))

    lazy val applefile: MediaType =
      MediaType("application", "applefile", compressible = false, binary = true)

    lazy val applixware: MediaType =
      MediaType("application", "applixware", compressible = false, binary = true, fileExtensions = List("aw"))

    lazy val appx: MediaType =
      MediaType("application", "appx", compressible = false, binary = true, fileExtensions = List("appx"))

    lazy val appxbundle: MediaType =
      MediaType("application", "appxbundle", compressible = false, binary = true, fileExtensions = List("appxbundle"))

    lazy val asyncapiJson: MediaType =
      MediaType("application", "asyncapi+json", compressible = true, binary = false)
    
    lazy val `asyncapi+json`: MediaType = asyncapiJson

    lazy val asyncapiYaml: MediaType =
      MediaType("application", "asyncapi+yaml", compressible = false, binary = true)
    
    lazy val `asyncapi+yaml`: MediaType = asyncapiYaml

    lazy val atJwt: MediaType =
      MediaType("application", "at+jwt", compressible = false, binary = true)
    
    lazy val `at+jwt`: MediaType = atJwt

    lazy val atf: MediaType =
      MediaType("application", "atf", compressible = false, binary = true)

    lazy val atfx: MediaType =
      MediaType("application", "atfx", compressible = false, binary = true)

    lazy val atomXml: MediaType =
      MediaType("application", "atom+xml", compressible = true, binary = true, fileExtensions = List("atom"))
    
    lazy val `atom+xml`: MediaType = atomXml

    lazy val atomcatXml: MediaType =
      MediaType("application", "atomcat+xml", compressible = true, binary = true, fileExtensions = List("atomcat"))
    
    lazy val `atomcat+xml`: MediaType = atomcatXml

    lazy val atomdeletedXml: MediaType =
      MediaType("application", "atomdeleted+xml", compressible = true, binary = true, fileExtensions = List("atomdeleted"))
    
    lazy val `atomdeleted+xml`: MediaType = atomdeletedXml

    lazy val atomicmail: MediaType =
      MediaType("application", "atomicmail", compressible = false, binary = true)

    lazy val atomsvcXml: MediaType =
      MediaType("application", "atomsvc+xml", compressible = true, binary = true, fileExtensions = List("atomsvc"))
    
    lazy val `atomsvc+xml`: MediaType = atomsvcXml

    lazy val atscDwdXml: MediaType =
      MediaType("application", "atsc-dwd+xml", compressible = true, binary = true, fileExtensions = List("dwd"))
    
    lazy val `atsc-dwd+xml`: MediaType = atscDwdXml

    lazy val atscDynamicEventMessage: MediaType =
      MediaType("application", "atsc-dynamic-event-message", compressible = false, binary = true)
    
    lazy val `atsc-dynamic-event-message`: MediaType = atscDynamicEventMessage

    lazy val atscHeldXml: MediaType =
      MediaType("application", "atsc-held+xml", compressible = true, binary = true, fileExtensions = List("held"))
    
    lazy val `atsc-held+xml`: MediaType = atscHeldXml

    lazy val atscRdtJson: MediaType =
      MediaType("application", "atsc-rdt+json", compressible = true, binary = false)
    
    lazy val `atsc-rdt+json`: MediaType = atscRdtJson

    lazy val atscRsatXml: MediaType =
      MediaType("application", "atsc-rsat+xml", compressible = true, binary = true, fileExtensions = List("rsat"))
    
    lazy val `atsc-rsat+xml`: MediaType = atscRsatXml

    lazy val atxml: MediaType =
      MediaType("application", "atxml", compressible = false, binary = true)

    lazy val authPolicyXml: MediaType =
      MediaType("application", "auth-policy+xml", compressible = true, binary = true)
    
    lazy val `auth-policy+xml`: MediaType = authPolicyXml

    lazy val automationmlAmlXml: MediaType =
      MediaType("application", "automationml-aml+xml", compressible = true, binary = true, fileExtensions = List("aml"))
    
    lazy val `automationml-aml+xml`: MediaType = automationmlAmlXml

    lazy val automationmlAmlxZip: MediaType =
      MediaType("application", "automationml-amlx+zip", compressible = false, binary = true, fileExtensions = List("amlx"))
    
    lazy val `automationml-amlx+zip`: MediaType = automationmlAmlxZip

    lazy val bacnetXddZip: MediaType =
      MediaType("application", "bacnet-xdd+zip", compressible = false, binary = true)
    
    lazy val `bacnet-xdd+zip`: MediaType = bacnetXddZip

    lazy val batchSmtp: MediaType =
      MediaType("application", "batch-smtp", compressible = false, binary = true)
    
    lazy val `batch-smtp`: MediaType = batchSmtp

    lazy val bdoc: MediaType =
      MediaType("application", "bdoc", compressible = false, binary = true, fileExtensions = List("bdoc"))

    lazy val beepXml: MediaType =
      MediaType("application", "beep+xml", compressible = true, binary = true)
    
    lazy val `beep+xml`: MediaType = beepXml

    lazy val bufr: MediaType =
      MediaType("application", "bufr", compressible = false, binary = true)

    lazy val c2pa: MediaType =
      MediaType("application", "c2pa", compressible = false, binary = true)

    lazy val calendarJson: MediaType =
      MediaType("application", "calendar+json", compressible = true, binary = false)
    
    lazy val `calendar+json`: MediaType = calendarJson

    lazy val calendarXml: MediaType =
      MediaType("application", "calendar+xml", compressible = true, binary = true, fileExtensions = List("xcs"))
    
    lazy val `calendar+xml`: MediaType = calendarXml

    lazy val callCompletion: MediaType =
      MediaType("application", "call-completion", compressible = false, binary = true)
    
    lazy val `call-completion`: MediaType = callCompletion

    lazy val cals1840: MediaType =
      MediaType("application", "cals-1840", compressible = false, binary = true)
    
    lazy val `cals-1840`: MediaType = cals1840

    lazy val captiveJson: MediaType =
      MediaType("application", "captive+json", compressible = true, binary = false)
    
    lazy val `captive+json`: MediaType = captiveJson

    lazy val cbor: MediaType =
      MediaType("application", "cbor", compressible = false, binary = true)

    lazy val cborSeq: MediaType =
      MediaType("application", "cbor-seq", compressible = false, binary = true)
    
    lazy val `cbor-seq`: MediaType = cborSeq

    lazy val cccex: MediaType =
      MediaType("application", "cccex", compressible = false, binary = true)

    lazy val ccmpXml: MediaType =
      MediaType("application", "ccmp+xml", compressible = true, binary = true)
    
    lazy val `ccmp+xml`: MediaType = ccmpXml

    lazy val ccxmlXml: MediaType =
      MediaType("application", "ccxml+xml", compressible = true, binary = true, fileExtensions = List("ccxml"))
    
    lazy val `ccxml+xml`: MediaType = ccxmlXml

    lazy val cdaXml: MediaType =
      MediaType("application", "cda+xml", compressible = true, binary = true)
    
    lazy val `cda+xml`: MediaType = cdaXml

    lazy val cdfxXml: MediaType =
      MediaType("application", "cdfx+xml", compressible = true, binary = true, fileExtensions = List("cdfx"))
    
    lazy val `cdfx+xml`: MediaType = cdfxXml

    lazy val cdmiCapability: MediaType =
      MediaType("application", "cdmi-capability", compressible = false, binary = true, fileExtensions = List("cdmia"))
    
    lazy val `cdmi-capability`: MediaType = cdmiCapability

    lazy val cdmiContainer: MediaType =
      MediaType("application", "cdmi-container", compressible = false, binary = true, fileExtensions = List("cdmic"))
    
    lazy val `cdmi-container`: MediaType = cdmiContainer

    lazy val cdmiDomain: MediaType =
      MediaType("application", "cdmi-domain", compressible = false, binary = true, fileExtensions = List("cdmid"))
    
    lazy val `cdmi-domain`: MediaType = cdmiDomain

    lazy val cdmiObject: MediaType =
      MediaType("application", "cdmi-object", compressible = false, binary = true, fileExtensions = List("cdmio"))
    
    lazy val `cdmi-object`: MediaType = cdmiObject

    lazy val cdmiQueue: MediaType =
      MediaType("application", "cdmi-queue", compressible = false, binary = true, fileExtensions = List("cdmiq"))
    
    lazy val `cdmi-queue`: MediaType = cdmiQueue

    lazy val cdni: MediaType =
      MediaType("application", "cdni", compressible = false, binary = true)

    lazy val ceCbor: MediaType =
      MediaType("application", "ce+cbor", compressible = false, binary = true)
    
    lazy val `ce+cbor`: MediaType = ceCbor

    lazy val cea: MediaType =
      MediaType("application", "cea", compressible = false, binary = true)

    lazy val cea2018Xml: MediaType =
      MediaType("application", "cea-2018+xml", compressible = true, binary = true)
    
    lazy val `cea-2018+xml`: MediaType = cea2018Xml

    lazy val cellmlXml: MediaType =
      MediaType("application", "cellml+xml", compressible = true, binary = true)
    
    lazy val `cellml+xml`: MediaType = cellmlXml

    lazy val cfw: MediaType =
      MediaType("application", "cfw", compressible = false, binary = true)

    lazy val cid: MediaType =
      MediaType("application", "cid", compressible = false, binary = true)

    lazy val cidEdhocCborSeq: MediaType =
      MediaType("application", "cid-edhoc+cbor-seq", compressible = false, binary = true)
    
    lazy val `cid-edhoc+cbor-seq`: MediaType = cidEdhocCborSeq

    lazy val cityJson: MediaType =
      MediaType("application", "city+json", compressible = true, binary = false)
    
    lazy val `city+json`: MediaType = cityJson

    lazy val cityJsonSeq: MediaType =
      MediaType("application", "city+json-seq", compressible = false, binary = true)
    
    lazy val `city+json-seq`: MediaType = cityJsonSeq

    lazy val clr: MediaType =
      MediaType("application", "clr", compressible = false, binary = true)

    lazy val clueXml: MediaType =
      MediaType("application", "clue+xml", compressible = true, binary = true)
    
    lazy val `clue+xml`: MediaType = clueXml

    lazy val clueInfoXml: MediaType =
      MediaType("application", "clue_info+xml", compressible = true, binary = true)
    
    lazy val `clue_info+xml`: MediaType = clueInfoXml

    lazy val cms: MediaType =
      MediaType("application", "cms", compressible = false, binary = true)

    lazy val cmwCbor: MediaType =
      MediaType("application", "cmw+cbor", compressible = false, binary = true)
    
    lazy val `cmw+cbor`: MediaType = cmwCbor

    lazy val cmwCose: MediaType =
      MediaType("application", "cmw+cose", compressible = false, binary = true)
    
    lazy val `cmw+cose`: MediaType = cmwCose

    lazy val cmwJson: MediaType =
      MediaType("application", "cmw+json", compressible = true, binary = false)
    
    lazy val `cmw+json`: MediaType = cmwJson

    lazy val cmwJws: MediaType =
      MediaType("application", "cmw+jws", compressible = false, binary = true)
    
    lazy val `cmw+jws`: MediaType = cmwJws

    lazy val cnrpXml: MediaType =
      MediaType("application", "cnrp+xml", compressible = true, binary = true)
    
    lazy val `cnrp+xml`: MediaType = cnrpXml

    lazy val coapEap: MediaType =
      MediaType("application", "coap-eap", compressible = false, binary = true)
    
    lazy val `coap-eap`: MediaType = coapEap

    lazy val coapGroupJson: MediaType =
      MediaType("application", "coap-group+json", compressible = true, binary = false)
    
    lazy val `coap-group+json`: MediaType = coapGroupJson

    lazy val coapPayload: MediaType =
      MediaType("application", "coap-payload", compressible = false, binary = true)
    
    lazy val `coap-payload`: MediaType = coapPayload

    lazy val commonground: MediaType =
      MediaType("application", "commonground", compressible = false, binary = true)

    lazy val conciseProblemDetailsCbor: MediaType =
      MediaType("application", "concise-problem-details+cbor", compressible = false, binary = true)
    
    lazy val `concise-problem-details+cbor`: MediaType = conciseProblemDetailsCbor

    lazy val conferenceInfoXml: MediaType =
      MediaType("application", "conference-info+xml", compressible = true, binary = true)
    
    lazy val `conference-info+xml`: MediaType = conferenceInfoXml

    lazy val cose: MediaType =
      MediaType("application", "cose", compressible = false, binary = true)

    lazy val coseKey: MediaType =
      MediaType("application", "cose-key", compressible = false, binary = true)
    
    lazy val `cose-key`: MediaType = coseKey

    lazy val coseKeySet: MediaType =
      MediaType("application", "cose-key-set", compressible = false, binary = true)
    
    lazy val `cose-key-set`: MediaType = coseKeySet

    lazy val coseX509: MediaType =
      MediaType("application", "cose-x509", compressible = false, binary = true)
    
    lazy val `cose-x509`: MediaType = coseX509

    lazy val cplXml: MediaType =
      MediaType("application", "cpl+xml", compressible = true, binary = true, fileExtensions = List("cpl"))
    
    lazy val `cpl+xml`: MediaType = cplXml

    lazy val csrattrs: MediaType =
      MediaType("application", "csrattrs", compressible = false, binary = true)

    lazy val cstaXml: MediaType =
      MediaType("application", "csta+xml", compressible = true, binary = true)
    
    lazy val `csta+xml`: MediaType = cstaXml

    lazy val cstadataXml: MediaType =
      MediaType("application", "cstadata+xml", compressible = true, binary = true)
    
    lazy val `cstadata+xml`: MediaType = cstadataXml

    lazy val csvmJson: MediaType =
      MediaType("application", "csvm+json", compressible = true, binary = false)
    
    lazy val `csvm+json`: MediaType = csvmJson

    lazy val cuSeeme: MediaType =
      MediaType("application", "cu-seeme", compressible = false, binary = true, fileExtensions = List("cu"))
    
    lazy val `cu-seeme`: MediaType = cuSeeme

    lazy val cwl: MediaType =
      MediaType("application", "cwl", compressible = false, binary = true, fileExtensions = List("cwl"))

    lazy val cwlJson: MediaType =
      MediaType("application", "cwl+json", compressible = true, binary = false)
    
    lazy val `cwl+json`: MediaType = cwlJson

    lazy val cwlYaml: MediaType =
      MediaType("application", "cwl+yaml", compressible = false, binary = true)
    
    lazy val `cwl+yaml`: MediaType = cwlYaml

    lazy val cwt: MediaType =
      MediaType("application", "cwt", compressible = false, binary = true)

    lazy val cybercash: MediaType =
      MediaType("application", "cybercash", compressible = false, binary = true)

    lazy val dart: MediaType =
      MediaType("application", "dart", compressible = true, binary = true)

    lazy val dashXml: MediaType =
      MediaType("application", "dash+xml", compressible = true, binary = true, fileExtensions = List("mpd"))
    
    lazy val `dash+xml`: MediaType = dashXml

    lazy val dashPatchXml: MediaType =
      MediaType("application", "dash-patch+xml", compressible = true, binary = true, fileExtensions = List("mpp"))
    
    lazy val `dash-patch+xml`: MediaType = dashPatchXml

    lazy val dashdelta: MediaType =
      MediaType("application", "dashdelta", compressible = false, binary = true)

    lazy val davmountXml: MediaType =
      MediaType("application", "davmount+xml", compressible = true, binary = true, fileExtensions = List("davmount"))
    
    lazy val `davmount+xml`: MediaType = davmountXml

    lazy val dcaRft: MediaType =
      MediaType("application", "dca-rft", compressible = false, binary = true)
    
    lazy val `dca-rft`: MediaType = dcaRft

    lazy val dcd: MediaType =
      MediaType("application", "dcd", compressible = false, binary = true)

    lazy val decDx: MediaType =
      MediaType("application", "dec-dx", compressible = false, binary = true)
    
    lazy val `dec-dx`: MediaType = decDx

    lazy val dialogInfoXml: MediaType =
      MediaType("application", "dialog-info+xml", compressible = true, binary = true)
    
    lazy val `dialog-info+xml`: MediaType = dialogInfoXml

    lazy val dicom: MediaType =
      MediaType("application", "dicom", compressible = false, binary = true, fileExtensions = List("dcm"))

    lazy val dicomJson: MediaType =
      MediaType("application", "dicom+json", compressible = true, binary = false)
    
    lazy val `dicom+json`: MediaType = dicomJson

    lazy val dicomXml: MediaType =
      MediaType("application", "dicom+xml", compressible = true, binary = true)
    
    lazy val `dicom+xml`: MediaType = dicomXml

    lazy val did: MediaType =
      MediaType("application", "did", compressible = false, binary = true)

    lazy val dii: MediaType =
      MediaType("application", "dii", compressible = false, binary = true)

    lazy val dit: MediaType =
      MediaType("application", "dit", compressible = false, binary = true)

    lazy val dns: MediaType =
      MediaType("application", "dns", compressible = false, binary = true)

    lazy val dnsJson: MediaType =
      MediaType("application", "dns+json", compressible = true, binary = false)
    
    lazy val `dns+json`: MediaType = dnsJson

    lazy val dnsMessage: MediaType =
      MediaType("application", "dns-message", compressible = false, binary = true)
    
    lazy val `dns-message`: MediaType = dnsMessage

    lazy val docbookXml: MediaType =
      MediaType("application", "docbook+xml", compressible = true, binary = true, fileExtensions = List("dbk"))
    
    lazy val `docbook+xml`: MediaType = docbookXml

    lazy val dotsCbor: MediaType =
      MediaType("application", "dots+cbor", compressible = false, binary = true)
    
    lazy val `dots+cbor`: MediaType = dotsCbor

    lazy val dpopJwt: MediaType =
      MediaType("application", "dpop+jwt", compressible = false, binary = true)
    
    lazy val `dpop+jwt`: MediaType = dpopJwt

    lazy val dskppXml: MediaType =
      MediaType("application", "dskpp+xml", compressible = true, binary = true)
    
    lazy val `dskpp+xml`: MediaType = dskppXml

    lazy val dsscDer: MediaType =
      MediaType("application", "dssc+der", compressible = false, binary = true, fileExtensions = List("dssc"))
    
    lazy val `dssc+der`: MediaType = dsscDer

    lazy val dsscXml: MediaType =
      MediaType("application", "dssc+xml", compressible = true, binary = true, fileExtensions = List("xdssc"))
    
    lazy val `dssc+xml`: MediaType = dsscXml

    lazy val dvcs: MediaType =
      MediaType("application", "dvcs", compressible = false, binary = true)

    lazy val eatCwt: MediaType =
      MediaType("application", "eat+cwt", compressible = false, binary = true)
    
    lazy val `eat+cwt`: MediaType = eatCwt

    lazy val eatJwt: MediaType =
      MediaType("application", "eat+jwt", compressible = false, binary = true)
    
    lazy val `eat+jwt`: MediaType = eatJwt

    lazy val eatBunCbor: MediaType =
      MediaType("application", "eat-bun+cbor", compressible = false, binary = true)
    
    lazy val `eat-bun+cbor`: MediaType = eatBunCbor

    lazy val eatBunJson: MediaType =
      MediaType("application", "eat-bun+json", compressible = true, binary = false)
    
    lazy val `eat-bun+json`: MediaType = eatBunJson

    lazy val eatUcsCbor: MediaType =
      MediaType("application", "eat-ucs+cbor", compressible = false, binary = true)
    
    lazy val `eat-ucs+cbor`: MediaType = eatUcsCbor

    lazy val eatUcsJson: MediaType =
      MediaType("application", "eat-ucs+json", compressible = true, binary = false)
    
    lazy val `eat-ucs+json`: MediaType = eatUcsJson

    lazy val ecmascript: MediaType =
      MediaType("application", "ecmascript", compressible = true, binary = true, fileExtensions = List("ecma"))

    lazy val edhocCborSeq: MediaType =
      MediaType("application", "edhoc+cbor-seq", compressible = false, binary = true)
    
    lazy val `edhoc+cbor-seq`: MediaType = edhocCborSeq

    lazy val ediConsent: MediaType =
      MediaType("application", "edi-consent", compressible = false, binary = true)
    
    lazy val `edi-consent`: MediaType = ediConsent

    lazy val ediX12: MediaType =
      MediaType("application", "edi-x12", compressible = false, binary = true)
    
    lazy val `edi-x12`: MediaType = ediX12

    lazy val edifact: MediaType =
      MediaType("application", "edifact", compressible = false, binary = true)

    lazy val efi: MediaType =
      MediaType("application", "efi", compressible = false, binary = true)

    lazy val elmJson: MediaType =
      MediaType("application", "elm+json", compressible = true, binary = false)
    
    lazy val `elm+json`: MediaType = elmJson

    lazy val elmXml: MediaType =
      MediaType("application", "elm+xml", compressible = true, binary = true)
    
    lazy val `elm+xml`: MediaType = elmXml

    lazy val emergencycalldataCapXml: MediaType =
      MediaType("application", "emergencycalldata.cap+xml", compressible = true, binary = true)
    
    lazy val `emergencycalldata.cap+xml`: MediaType = emergencycalldataCapXml

    lazy val emergencycalldataCommentXml: MediaType =
      MediaType("application", "emergencycalldata.comment+xml", compressible = true, binary = true)
    
    lazy val `emergencycalldata.comment+xml`: MediaType = emergencycalldataCommentXml

    lazy val emergencycalldataControlXml: MediaType =
      MediaType("application", "emergencycalldata.control+xml", compressible = true, binary = true)
    
    lazy val `emergencycalldata.control+xml`: MediaType = emergencycalldataControlXml

    lazy val emergencycalldataDeviceinfoXml: MediaType =
      MediaType("application", "emergencycalldata.deviceinfo+xml", compressible = true, binary = true)
    
    lazy val `emergencycalldata.deviceinfo+xml`: MediaType = emergencycalldataDeviceinfoXml

    lazy val emergencycalldataEcallMsd: MediaType =
      MediaType("application", "emergencycalldata.ecall.msd", compressible = false, binary = true)
    
    lazy val `emergencycalldata.ecall.msd`: MediaType = emergencycalldataEcallMsd

    lazy val emergencycalldataLegacyesnJson: MediaType =
      MediaType("application", "emergencycalldata.legacyesn+json", compressible = true, binary = false)
    
    lazy val `emergencycalldata.legacyesn+json`: MediaType = emergencycalldataLegacyesnJson

    lazy val emergencycalldataProviderinfoXml: MediaType =
      MediaType("application", "emergencycalldata.providerinfo+xml", compressible = true, binary = true)
    
    lazy val `emergencycalldata.providerinfo+xml`: MediaType = emergencycalldataProviderinfoXml

    lazy val emergencycalldataServiceinfoXml: MediaType =
      MediaType("application", "emergencycalldata.serviceinfo+xml", compressible = true, binary = true)
    
    lazy val `emergencycalldata.serviceinfo+xml`: MediaType = emergencycalldataServiceinfoXml

    lazy val emergencycalldataSubscriberinfoXml: MediaType =
      MediaType("application", "emergencycalldata.subscriberinfo+xml", compressible = true, binary = true)
    
    lazy val `emergencycalldata.subscriberinfo+xml`: MediaType = emergencycalldataSubscriberinfoXml

    lazy val emergencycalldataVedsXml: MediaType =
      MediaType("application", "emergencycalldata.veds+xml", compressible = true, binary = true)
    
    lazy val `emergencycalldata.veds+xml`: MediaType = emergencycalldataVedsXml

    lazy val emmaXml: MediaType =
      MediaType("application", "emma+xml", compressible = true, binary = true, fileExtensions = List("emma"))
    
    lazy val `emma+xml`: MediaType = emmaXml

    lazy val emotionmlXml: MediaType =
      MediaType("application", "emotionml+xml", compressible = true, binary = true, fileExtensions = List("emotionml"))
    
    lazy val `emotionml+xml`: MediaType = emotionmlXml

    lazy val encaprtp: MediaType =
      MediaType("application", "encaprtp", compressible = false, binary = true)

    lazy val entityStatementJwt: MediaType =
      MediaType("application", "entity-statement+jwt", compressible = false, binary = true)
    
    lazy val `entity-statement+jwt`: MediaType = entityStatementJwt

    lazy val eppXml: MediaType =
      MediaType("application", "epp+xml", compressible = true, binary = true)
    
    lazy val `epp+xml`: MediaType = eppXml

    lazy val epubZip: MediaType =
      MediaType("application", "epub+zip", compressible = false, binary = true, fileExtensions = List("epub"))
    
    lazy val `epub+zip`: MediaType = epubZip

    lazy val eshop: MediaType =
      MediaType("application", "eshop", compressible = false, binary = true)

    lazy val exi: MediaType =
      MediaType("application", "exi", compressible = false, binary = true, fileExtensions = List("exi"))

    lazy val expectCtReportJson: MediaType =
      MediaType("application", "expect-ct-report+json", compressible = true, binary = false)
    
    lazy val `expect-ct-report+json`: MediaType = expectCtReportJson

    lazy val explicitRegistrationResponseJwt: MediaType =
      MediaType("application", "explicit-registration-response+jwt", compressible = false, binary = true)
    
    lazy val `explicit-registration-response+jwt`: MediaType = explicitRegistrationResponseJwt

    lazy val express: MediaType =
      MediaType("application", "express", compressible = false, binary = true, fileExtensions = List("exp"))

    lazy val fastinfoset: MediaType =
      MediaType("application", "fastinfoset", compressible = false, binary = true)

    lazy val fastsoap: MediaType =
      MediaType("application", "fastsoap", compressible = false, binary = true)

    lazy val fdf: MediaType =
      MediaType("application", "fdf", compressible = false, binary = true, fileExtensions = List("fdf"))

    lazy val fdtXml: MediaType =
      MediaType("application", "fdt+xml", compressible = true, binary = true, fileExtensions = List("fdt"))
    
    lazy val `fdt+xml`: MediaType = fdtXml

    lazy val fhirJson: MediaType =
      MediaType("application", "fhir+json", compressible = true, binary = false)
    
    lazy val `fhir+json`: MediaType = fhirJson

    lazy val fhirXml: MediaType =
      MediaType("application", "fhir+xml", compressible = true, binary = true)
    
    lazy val `fhir+xml`: MediaType = fhirXml

    lazy val fidoTrustedAppsJson: MediaType =
      MediaType("application", "fido.trusted-apps+json", compressible = true, binary = false)
    
    lazy val `fido.trusted-apps+json`: MediaType = fidoTrustedAppsJson

    lazy val fits: MediaType =
      MediaType("application", "fits", compressible = false, binary = true)

    lazy val flexfec: MediaType =
      MediaType("application", "flexfec", compressible = false, binary = true)

    lazy val fontSfnt: MediaType =
      MediaType("application", "font-sfnt", compressible = false, binary = true)
    
    lazy val `font-sfnt`: MediaType = fontSfnt

    lazy val fontTdpfr: MediaType =
      MediaType("application", "font-tdpfr", compressible = false, binary = true, fileExtensions = List("pfr"))
    
    lazy val `font-tdpfr`: MediaType = fontTdpfr

    lazy val fontWoff: MediaType =
      MediaType("application", "font-woff", compressible = false, binary = true)
    
    lazy val `font-woff`: MediaType = fontWoff

    lazy val frameworkAttributesXml: MediaType =
      MediaType("application", "framework-attributes+xml", compressible = true, binary = true)
    
    lazy val `framework-attributes+xml`: MediaType = frameworkAttributesXml

    lazy val geoJson: MediaType =
      MediaType("application", "geo+json", compressible = true, binary = false, fileExtensions = List("geojson"))
    
    lazy val `geo+json`: MediaType = geoJson

    lazy val geoJsonSeq: MediaType =
      MediaType("application", "geo+json-seq", compressible = false, binary = true)
    
    lazy val `geo+json-seq`: MediaType = geoJsonSeq

    lazy val geofeedCsv: MediaType =
      MediaType("application", "geofeed+csv", compressible = false, binary = true)
    
    lazy val `geofeed+csv`: MediaType = geofeedCsv

    lazy val geopackageSqlite3: MediaType =
      MediaType("application", "geopackage+sqlite3", compressible = false, binary = true)
    
    lazy val `geopackage+sqlite3`: MediaType = geopackageSqlite3

    lazy val geoposeJson: MediaType =
      MediaType("application", "geopose+json", compressible = true, binary = false)
    
    lazy val `geopose+json`: MediaType = geoposeJson

    lazy val geoxacmlJson: MediaType =
      MediaType("application", "geoxacml+json", compressible = true, binary = false)
    
    lazy val `geoxacml+json`: MediaType = geoxacmlJson

    lazy val geoxacmlXml: MediaType =
      MediaType("application", "geoxacml+xml", compressible = true, binary = true)
    
    lazy val `geoxacml+xml`: MediaType = geoxacmlXml

    lazy val gltfBuffer: MediaType =
      MediaType("application", "gltf-buffer", compressible = false, binary = true)
    
    lazy val `gltf-buffer`: MediaType = gltfBuffer

    lazy val gmlXml: MediaType =
      MediaType("application", "gml+xml", compressible = true, binary = true, fileExtensions = List("gml"))
    
    lazy val `gml+xml`: MediaType = gmlXml

    lazy val gnapBindingJws: MediaType =
      MediaType("application", "gnap-binding-jws", compressible = false, binary = true)
    
    lazy val `gnap-binding-jws`: MediaType = gnapBindingJws

    lazy val gnapBindingJwsd: MediaType =
      MediaType("application", "gnap-binding-jwsd", compressible = false, binary = true)
    
    lazy val `gnap-binding-jwsd`: MediaType = gnapBindingJwsd

    lazy val gnapBindingRotationJws: MediaType =
      MediaType("application", "gnap-binding-rotation-jws", compressible = false, binary = true)
    
    lazy val `gnap-binding-rotation-jws`: MediaType = gnapBindingRotationJws

    lazy val gnapBindingRotationJwsd: MediaType =
      MediaType("application", "gnap-binding-rotation-jwsd", compressible = false, binary = true)
    
    lazy val `gnap-binding-rotation-jwsd`: MediaType = gnapBindingRotationJwsd

    lazy val gpxXml: MediaType =
      MediaType("application", "gpx+xml", compressible = true, binary = true, fileExtensions = List("gpx"))
    
    lazy val `gpx+xml`: MediaType = gpxXml

    lazy val grib: MediaType =
      MediaType("application", "grib", compressible = false, binary = true)

    lazy val gxf: MediaType =
      MediaType("application", "gxf", compressible = false, binary = true, fileExtensions = List("gxf"))

    lazy val gzip: MediaType =
      MediaType("application", "gzip", compressible = false, binary = true, fileExtensions = List("gz"))

    lazy val h224: MediaType =
      MediaType("application", "h224", compressible = false, binary = true)

    lazy val heldXml: MediaType =
      MediaType("application", "held+xml", compressible = true, binary = true)
    
    lazy val `held+xml`: MediaType = heldXml

    lazy val hjson: MediaType =
      MediaType("application", "hjson", compressible = false, binary = false, fileExtensions = List("hjson"))

    lazy val hl7v2Xml: MediaType =
      MediaType("application", "hl7v2+xml", compressible = true, binary = true)
    
    lazy val `hl7v2+xml`: MediaType = hl7v2Xml

    lazy val http: MediaType =
      MediaType("application", "http", compressible = false, binary = true)

    lazy val hyperstudio: MediaType =
      MediaType("application", "hyperstudio", compressible = false, binary = true, fileExtensions = List("stk"))

    lazy val ibeKeyRequestXml: MediaType =
      MediaType("application", "ibe-key-request+xml", compressible = true, binary = true)
    
    lazy val `ibe-key-request+xml`: MediaType = ibeKeyRequestXml

    lazy val ibePkgReplyXml: MediaType =
      MediaType("application", "ibe-pkg-reply+xml", compressible = true, binary = true)
    
    lazy val `ibe-pkg-reply+xml`: MediaType = ibePkgReplyXml

    lazy val ibePpData: MediaType =
      MediaType("application", "ibe-pp-data", compressible = false, binary = true)
    
    lazy val `ibe-pp-data`: MediaType = ibePpData

    lazy val iges: MediaType =
      MediaType("application", "iges", compressible = false, binary = true)

    lazy val imIscomposingXml: MediaType =
      MediaType("application", "im-iscomposing+xml", compressible = true, binary = true)
    
    lazy val `im-iscomposing+xml`: MediaType = imIscomposingXml

    lazy val index: MediaType =
      MediaType("application", "index", compressible = false, binary = true)

    lazy val indexCmd: MediaType =
      MediaType("application", "index.cmd", compressible = false, binary = true)
    
    lazy val `index.cmd`: MediaType = indexCmd

    lazy val indexObj: MediaType =
      MediaType("application", "index.obj", compressible = false, binary = true)
    
    lazy val `index.obj`: MediaType = indexObj

    lazy val indexResponse: MediaType =
      MediaType("application", "index.response", compressible = false, binary = true)
    
    lazy val `index.response`: MediaType = indexResponse

    lazy val indexVnd: MediaType =
      MediaType("application", "index.vnd", compressible = false, binary = true)
    
    lazy val `index.vnd`: MediaType = indexVnd

    lazy val inkmlXml: MediaType =
      MediaType("application", "inkml+xml", compressible = true, binary = true, fileExtensions = List("ink", "inkml"))
    
    lazy val `inkml+xml`: MediaType = inkmlXml

    lazy val iotp: MediaType =
      MediaType("application", "iotp", compressible = false, binary = true)

    lazy val ipfix: MediaType =
      MediaType("application", "ipfix", compressible = false, binary = true, fileExtensions = List("ipfix"))

    lazy val ipp: MediaType =
      MediaType("application", "ipp", compressible = false, binary = true)

    lazy val isup: MediaType =
      MediaType("application", "isup", compressible = false, binary = true)

    lazy val itsXml: MediaType =
      MediaType("application", "its+xml", compressible = true, binary = true, fileExtensions = List("its"))
    
    lazy val `its+xml`: MediaType = itsXml

    lazy val javaArchive: MediaType =
      MediaType("application", "java-archive", compressible = false, binary = true, fileExtensions = List("jar", "war", "ear"))
    
    lazy val `java-archive`: MediaType = javaArchive

    lazy val javaSerializedObject: MediaType =
      MediaType("application", "java-serialized-object", compressible = false, binary = true, fileExtensions = List("ser"))
    
    lazy val `java-serialized-object`: MediaType = javaSerializedObject

    lazy val javaVm: MediaType =
      MediaType("application", "java-vm", compressible = false, binary = true, fileExtensions = List("class"))
    
    lazy val `java-vm`: MediaType = javaVm

    lazy val javascript: MediaType =
      MediaType("application", "javascript", compressible = true, binary = false, fileExtensions = List("js"))

    lazy val jf2feedJson: MediaType =
      MediaType("application", "jf2feed+json", compressible = true, binary = false)
    
    lazy val `jf2feed+json`: MediaType = jf2feedJson

    lazy val jose: MediaType =
      MediaType("application", "jose", compressible = false, binary = true)

    lazy val joseJson: MediaType =
      MediaType("application", "jose+json", compressible = true, binary = false)
    
    lazy val `jose+json`: MediaType = joseJson

    lazy val jrdJson: MediaType =
      MediaType("application", "jrd+json", compressible = true, binary = false)
    
    lazy val `jrd+json`: MediaType = jrdJson

    lazy val jscalendarJson: MediaType =
      MediaType("application", "jscalendar+json", compressible = true, binary = false)
    
    lazy val `jscalendar+json`: MediaType = jscalendarJson

    lazy val jscontactJson: MediaType =
      MediaType("application", "jscontact+json", compressible = true, binary = false)
    
    lazy val `jscontact+json`: MediaType = jscontactJson

    lazy val json: MediaType =
      MediaType("application", "json", compressible = true, binary = false, fileExtensions = List("json", "map"))

    lazy val jsonPatchJson: MediaType =
      MediaType("application", "json-patch+json", compressible = true, binary = false)
    
    lazy val `json-patch+json`: MediaType = jsonPatchJson

    lazy val jsonPatchQueryJson: MediaType =
      MediaType("application", "json-patch-query+json", compressible = true, binary = false)
    
    lazy val `json-patch-query+json`: MediaType = jsonPatchQueryJson

    lazy val jsonSeq: MediaType =
      MediaType("application", "json-seq", compressible = false, binary = true)
    
    lazy val `json-seq`: MediaType = jsonSeq

    lazy val json5: MediaType =
      MediaType("application", "json5", compressible = false, binary = true, fileExtensions = List("json5"))

    lazy val jsonmlJson: MediaType =
      MediaType("application", "jsonml+json", compressible = true, binary = false, fileExtensions = List("jsonml"))
    
    lazy val `jsonml+json`: MediaType = jsonmlJson

    lazy val jsonpath: MediaType =
      MediaType("application", "jsonpath", compressible = false, binary = true)

    lazy val jwkJson: MediaType =
      MediaType("application", "jwk+json", compressible = true, binary = false)
    
    lazy val `jwk+json`: MediaType = jwkJson

    lazy val jwkSetJson: MediaType =
      MediaType("application", "jwk-set+json", compressible = true, binary = false)
    
    lazy val `jwk-set+json`: MediaType = jwkSetJson

    lazy val jwkSetJwt: MediaType =
      MediaType("application", "jwk-set+jwt", compressible = false, binary = true)
    
    lazy val `jwk-set+jwt`: MediaType = jwkSetJwt

    lazy val jwt: MediaType =
      MediaType("application", "jwt", compressible = false, binary = true)

    lazy val kbJwt: MediaType =
      MediaType("application", "kb+jwt", compressible = false, binary = true)
    
    lazy val `kb+jwt`: MediaType = kbJwt

    lazy val kblXml: MediaType =
      MediaType("application", "kbl+xml", compressible = true, binary = true, fileExtensions = List("kbl"))
    
    lazy val `kbl+xml`: MediaType = kblXml

    lazy val kpmlRequestXml: MediaType =
      MediaType("application", "kpml-request+xml", compressible = true, binary = true)
    
    lazy val `kpml-request+xml`: MediaType = kpmlRequestXml

    lazy val kpmlResponseXml: MediaType =
      MediaType("application", "kpml-response+xml", compressible = true, binary = true)
    
    lazy val `kpml-response+xml`: MediaType = kpmlResponseXml

    lazy val ldJson: MediaType =
      MediaType("application", "ld+json", compressible = true, binary = false, fileExtensions = List("jsonld"))
    
    lazy val `ld+json`: MediaType = ldJson

    lazy val lgrXml: MediaType =
      MediaType("application", "lgr+xml", compressible = true, binary = true, fileExtensions = List("lgr"))
    
    lazy val `lgr+xml`: MediaType = lgrXml

    lazy val linkFormat: MediaType =
      MediaType("application", "link-format", compressible = false, binary = true)
    
    lazy val `link-format`: MediaType = linkFormat

    lazy val linkset: MediaType =
      MediaType("application", "linkset", compressible = false, binary = true)

    lazy val linksetJson: MediaType =
      MediaType("application", "linkset+json", compressible = true, binary = false)
    
    lazy val `linkset+json`: MediaType = linksetJson

    lazy val loadControlXml: MediaType =
      MediaType("application", "load-control+xml", compressible = true, binary = true)
    
    lazy val `load-control+xml`: MediaType = loadControlXml

    lazy val logoutJwt: MediaType =
      MediaType("application", "logout+jwt", compressible = false, binary = true)
    
    lazy val `logout+jwt`: MediaType = logoutJwt

    lazy val lostXml: MediaType =
      MediaType("application", "lost+xml", compressible = true, binary = true, fileExtensions = List("lostxml"))
    
    lazy val `lost+xml`: MediaType = lostXml

    lazy val lostsyncXml: MediaType =
      MediaType("application", "lostsync+xml", compressible = true, binary = true)
    
    lazy val `lostsync+xml`: MediaType = lostsyncXml

    lazy val lpfZip: MediaType =
      MediaType("application", "lpf+zip", compressible = false, binary = true)
    
    lazy val `lpf+zip`: MediaType = lpfZip

    lazy val lxf: MediaType =
      MediaType("application", "lxf", compressible = false, binary = true)

    lazy val macBinhex40: MediaType =
      MediaType("application", "mac-binhex40", compressible = false, binary = true, fileExtensions = List("hqx"))
    
    lazy val `mac-binhex40`: MediaType = macBinhex40

    lazy val macCompactpro: MediaType =
      MediaType("application", "mac-compactpro", compressible = false, binary = true, fileExtensions = List("cpt"))
    
    lazy val `mac-compactpro`: MediaType = macCompactpro

    lazy val macwriteii: MediaType =
      MediaType("application", "macwriteii", compressible = false, binary = true)

    lazy val madsXml: MediaType =
      MediaType("application", "mads+xml", compressible = true, binary = true, fileExtensions = List("mads"))
    
    lazy val `mads+xml`: MediaType = madsXml

    lazy val manifestJson: MediaType =
      MediaType("application", "manifest+json", compressible = true, binary = false, fileExtensions = List("webmanifest"))
    
    lazy val `manifest+json`: MediaType = manifestJson

    lazy val marc: MediaType =
      MediaType("application", "marc", compressible = false, binary = true, fileExtensions = List("mrc"))

    lazy val marcxmlXml: MediaType =
      MediaType("application", "marcxml+xml", compressible = true, binary = true, fileExtensions = List("mrcx"))
    
    lazy val `marcxml+xml`: MediaType = marcxmlXml

    lazy val mathematica: MediaType =
      MediaType("application", "mathematica", compressible = false, binary = true, fileExtensions = List("ma", "nb", "mb"))

    lazy val mathmlXml: MediaType =
      MediaType("application", "mathml+xml", compressible = true, binary = true, fileExtensions = List("mathml"))
    
    lazy val `mathml+xml`: MediaType = mathmlXml

    lazy val mathmlContentXml: MediaType =
      MediaType("application", "mathml-content+xml", compressible = true, binary = true)
    
    lazy val `mathml-content+xml`: MediaType = mathmlContentXml

    lazy val mathmlPresentationXml: MediaType =
      MediaType("application", "mathml-presentation+xml", compressible = true, binary = true)
    
    lazy val `mathml-presentation+xml`: MediaType = mathmlPresentationXml

    lazy val mbmsAssociatedProcedureDescriptionXml: MediaType =
      MediaType("application", "mbms-associated-procedure-description+xml", compressible = true, binary = true)
    
    lazy val `mbms-associated-procedure-description+xml`: MediaType = mbmsAssociatedProcedureDescriptionXml

    lazy val mbmsDeregisterXml: MediaType =
      MediaType("application", "mbms-deregister+xml", compressible = true, binary = true)
    
    lazy val `mbms-deregister+xml`: MediaType = mbmsDeregisterXml

    lazy val mbmsEnvelopeXml: MediaType =
      MediaType("application", "mbms-envelope+xml", compressible = true, binary = true)
    
    lazy val `mbms-envelope+xml`: MediaType = mbmsEnvelopeXml

    lazy val mbmsMskXml: MediaType =
      MediaType("application", "mbms-msk+xml", compressible = true, binary = true)
    
    lazy val `mbms-msk+xml`: MediaType = mbmsMskXml

    lazy val mbmsMskResponseXml: MediaType =
      MediaType("application", "mbms-msk-response+xml", compressible = true, binary = true)
    
    lazy val `mbms-msk-response+xml`: MediaType = mbmsMskResponseXml

    lazy val mbmsProtectionDescriptionXml: MediaType =
      MediaType("application", "mbms-protection-description+xml", compressible = true, binary = true)
    
    lazy val `mbms-protection-description+xml`: MediaType = mbmsProtectionDescriptionXml

    lazy val mbmsReceptionReportXml: MediaType =
      MediaType("application", "mbms-reception-report+xml", compressible = true, binary = true)
    
    lazy val `mbms-reception-report+xml`: MediaType = mbmsReceptionReportXml

    lazy val mbmsRegisterXml: MediaType =
      MediaType("application", "mbms-register+xml", compressible = true, binary = true)
    
    lazy val `mbms-register+xml`: MediaType = mbmsRegisterXml

    lazy val mbmsRegisterResponseXml: MediaType =
      MediaType("application", "mbms-register-response+xml", compressible = true, binary = true)
    
    lazy val `mbms-register-response+xml`: MediaType = mbmsRegisterResponseXml

    lazy val mbmsScheduleXml: MediaType =
      MediaType("application", "mbms-schedule+xml", compressible = true, binary = true)
    
    lazy val `mbms-schedule+xml`: MediaType = mbmsScheduleXml

    lazy val mbmsUserServiceDescriptionXml: MediaType =
      MediaType("application", "mbms-user-service-description+xml", compressible = true, binary = true)
    
    lazy val `mbms-user-service-description+xml`: MediaType = mbmsUserServiceDescriptionXml

    lazy val mbox: MediaType =
      MediaType("application", "mbox", compressible = false, binary = true, fileExtensions = List("mbox"))

    lazy val measuredComponentCbor: MediaType =
      MediaType("application", "measured-component+cbor", compressible = false, binary = true)
    
    lazy val `measured-component+cbor`: MediaType = measuredComponentCbor

    lazy val measuredComponentJson: MediaType =
      MediaType("application", "measured-component+json", compressible = true, binary = false)
    
    lazy val `measured-component+json`: MediaType = measuredComponentJson

    lazy val mediaPolicyDatasetXml: MediaType =
      MediaType("application", "media-policy-dataset+xml", compressible = true, binary = true, fileExtensions = List("mpf"))
    
    lazy val `media-policy-dataset+xml`: MediaType = mediaPolicyDatasetXml

    lazy val mediaControlXml: MediaType =
      MediaType("application", "media_control+xml", compressible = true, binary = true)
    
    lazy val `media_control+xml`: MediaType = mediaControlXml

    lazy val mediaservercontrolXml: MediaType =
      MediaType("application", "mediaservercontrol+xml", compressible = true, binary = true, fileExtensions = List("mscml"))
    
    lazy val `mediaservercontrol+xml`: MediaType = mediaservercontrolXml

    lazy val mergePatchJson: MediaType =
      MediaType("application", "merge-patch+json", compressible = true, binary = false)
    
    lazy val `merge-patch+json`: MediaType = mergePatchJson

    lazy val metalinkXml: MediaType =
      MediaType("application", "metalink+xml", compressible = true, binary = true, fileExtensions = List("metalink"))
    
    lazy val `metalink+xml`: MediaType = metalinkXml

    lazy val metalink4Xml: MediaType =
      MediaType("application", "metalink4+xml", compressible = true, binary = true, fileExtensions = List("meta4"))
    
    lazy val `metalink4+xml`: MediaType = metalink4Xml

    lazy val metsXml: MediaType =
      MediaType("application", "mets+xml", compressible = true, binary = true, fileExtensions = List("mets"))
    
    lazy val `mets+xml`: MediaType = metsXml

    lazy val mf4: MediaType =
      MediaType("application", "mf4", compressible = false, binary = true)

    lazy val mikey: MediaType =
      MediaType("application", "mikey", compressible = false, binary = true)

    lazy val mipc: MediaType =
      MediaType("application", "mipc", compressible = false, binary = true)

    lazy val missingBlocksCborSeq: MediaType =
      MediaType("application", "missing-blocks+cbor-seq", compressible = false, binary = true)
    
    lazy val `missing-blocks+cbor-seq`: MediaType = missingBlocksCborSeq

    lazy val mmtAeiXml: MediaType =
      MediaType("application", "mmt-aei+xml", compressible = true, binary = true, fileExtensions = List("maei"))
    
    lazy val `mmt-aei+xml`: MediaType = mmtAeiXml

    lazy val mmtUsdXml: MediaType =
      MediaType("application", "mmt-usd+xml", compressible = true, binary = true, fileExtensions = List("musd"))
    
    lazy val `mmt-usd+xml`: MediaType = mmtUsdXml

    lazy val modsXml: MediaType =
      MediaType("application", "mods+xml", compressible = true, binary = true, fileExtensions = List("mods"))
    
    lazy val `mods+xml`: MediaType = modsXml

    lazy val mossKeys: MediaType =
      MediaType("application", "moss-keys", compressible = false, binary = true)
    
    lazy val `moss-keys`: MediaType = mossKeys

    lazy val mossSignature: MediaType =
      MediaType("application", "moss-signature", compressible = false, binary = true)
    
    lazy val `moss-signature`: MediaType = mossSignature

    lazy val mosskeyData: MediaType =
      MediaType("application", "mosskey-data", compressible = false, binary = true)
    
    lazy val `mosskey-data`: MediaType = mosskeyData

    lazy val mosskeyRequest: MediaType =
      MediaType("application", "mosskey-request", compressible = false, binary = true)
    
    lazy val `mosskey-request`: MediaType = mosskeyRequest

    lazy val mp21: MediaType =
      MediaType("application", "mp21", compressible = false, binary = true, fileExtensions = List("m21", "mp21"))

    lazy val mp4: MediaType =
      MediaType("application", "mp4", compressible = false, binary = true, fileExtensions = List("mp4", "mpg4", "mp4s", "m4p"))

    lazy val mpeg4Generic: MediaType =
      MediaType("application", "mpeg4-generic", compressible = false, binary = true)
    
    lazy val `mpeg4-generic`: MediaType = mpeg4Generic

    lazy val mpeg4Iod: MediaType =
      MediaType("application", "mpeg4-iod", compressible = false, binary = true)
    
    lazy val `mpeg4-iod`: MediaType = mpeg4Iod

    lazy val mpeg4IodXmt: MediaType =
      MediaType("application", "mpeg4-iod-xmt", compressible = false, binary = true)
    
    lazy val `mpeg4-iod-xmt`: MediaType = mpeg4IodXmt

    lazy val mrbConsumerXml: MediaType =
      MediaType("application", "mrb-consumer+xml", compressible = true, binary = true)
    
    lazy val `mrb-consumer+xml`: MediaType = mrbConsumerXml

    lazy val mrbPublishXml: MediaType =
      MediaType("application", "mrb-publish+xml", compressible = true, binary = true)
    
    lazy val `mrb-publish+xml`: MediaType = mrbPublishXml

    lazy val mscIvrXml: MediaType =
      MediaType("application", "msc-ivr+xml", compressible = true, binary = true)
    
    lazy val `msc-ivr+xml`: MediaType = mscIvrXml

    lazy val mscMixerXml: MediaType =
      MediaType("application", "msc-mixer+xml", compressible = true, binary = true)
    
    lazy val `msc-mixer+xml`: MediaType = mscMixerXml

    lazy val msix: MediaType =
      MediaType("application", "msix", compressible = false, binary = true, fileExtensions = List("msix"))

    lazy val msixbundle: MediaType =
      MediaType("application", "msixbundle", compressible = false, binary = true, fileExtensions = List("msixbundle"))

    lazy val msword: MediaType =
      MediaType("application", "msword", compressible = false, binary = true, fileExtensions = List("doc", "dot"))

    lazy val mudJson: MediaType =
      MediaType("application", "mud+json", compressible = true, binary = false)
    
    lazy val `mud+json`: MediaType = mudJson

    lazy val multipartCore: MediaType =
      MediaType("application", "multipart-core", compressible = false, binary = true)
    
    lazy val `multipart-core`: MediaType = multipartCore

    lazy val mxf: MediaType =
      MediaType("application", "mxf", compressible = false, binary = true, fileExtensions = List("mxf"))

    lazy val nQuads: MediaType =
      MediaType("application", "n-quads", compressible = false, binary = true, fileExtensions = List("nq"))
    
    lazy val `n-quads`: MediaType = nQuads

    lazy val nTriples: MediaType =
      MediaType("application", "n-triples", compressible = false, binary = true, fileExtensions = List("nt"))
    
    lazy val `n-triples`: MediaType = nTriples

    lazy val nasdata: MediaType =
      MediaType("application", "nasdata", compressible = false, binary = true)

    lazy val newsCheckgroups: MediaType =
      MediaType("application", "news-checkgroups", compressible = false, binary = true)
    
    lazy val `news-checkgroups`: MediaType = newsCheckgroups

    lazy val newsGroupinfo: MediaType =
      MediaType("application", "news-groupinfo", compressible = false, binary = true)
    
    lazy val `news-groupinfo`: MediaType = newsGroupinfo

    lazy val newsTransmission: MediaType =
      MediaType("application", "news-transmission", compressible = false, binary = true)
    
    lazy val `news-transmission`: MediaType = newsTransmission

    lazy val nlsmlXml: MediaType =
      MediaType("application", "nlsml+xml", compressible = true, binary = true)
    
    lazy val `nlsml+xml`: MediaType = nlsmlXml

    lazy val node: MediaType =
      MediaType("application", "node", compressible = false, binary = true, fileExtensions = List("cjs"))

    lazy val nss: MediaType =
      MediaType("application", "nss", compressible = false, binary = true)

    lazy val oauthAuthzReqJwt: MediaType =
      MediaType("application", "oauth-authz-req+jwt", compressible = false, binary = true)
    
    lazy val `oauth-authz-req+jwt`: MediaType = oauthAuthzReqJwt

    lazy val obliviousDnsMessage: MediaType =
      MediaType("application", "oblivious-dns-message", compressible = false, binary = true)
    
    lazy val `oblivious-dns-message`: MediaType = obliviousDnsMessage

    lazy val ocspRequest: MediaType =
      MediaType("application", "ocsp-request", compressible = false, binary = true)
    
    lazy val `ocsp-request`: MediaType = ocspRequest

    lazy val ocspResponse: MediaType =
      MediaType("application", "ocsp-response", compressible = false, binary = true)
    
    lazy val `ocsp-response`: MediaType = ocspResponse

    lazy val octetStream: MediaType =
      MediaType("application", "octet-stream", compressible = true, binary = true, fileExtensions = List("bin", "dms", "lrf", "mar", "so", "dist", "distz", "pkg", "bpk", "dump", "elc", "deploy", "exe", "dll", "deb", "dmg", "iso", "img", "msi", "msp", "msm", "buffer"))
    
    lazy val `octet-stream`: MediaType = octetStream

    lazy val oda: MediaType =
      MediaType("application", "oda", compressible = false, binary = true, fileExtensions = List("oda"))

    lazy val odmXml: MediaType =
      MediaType("application", "odm+xml", compressible = true, binary = true)
    
    lazy val `odm+xml`: MediaType = odmXml

    lazy val odx: MediaType =
      MediaType("application", "odx", compressible = false, binary = true)

    lazy val oebpsPackageXml: MediaType =
      MediaType("application", "oebps-package+xml", compressible = true, binary = true, fileExtensions = List("opf"))
    
    lazy val `oebps-package+xml`: MediaType = oebpsPackageXml

    lazy val ogg: MediaType =
      MediaType("application", "ogg", compressible = false, binary = true, fileExtensions = List("ogx"))

    lazy val ohttpKeys: MediaType =
      MediaType("application", "ohttp-keys", compressible = false, binary = true)
    
    lazy val `ohttp-keys`: MediaType = ohttpKeys

    lazy val omdocXml: MediaType =
      MediaType("application", "omdoc+xml", compressible = true, binary = true, fileExtensions = List("omdoc"))
    
    lazy val `omdoc+xml`: MediaType = omdocXml

    lazy val onenote: MediaType =
      MediaType("application", "onenote", compressible = false, binary = true, fileExtensions = List("onetoc", "onetoc2", "onetmp", "onepkg", "one", "onea"))

    lazy val opcNodesetXml: MediaType =
      MediaType("application", "opc-nodeset+xml", compressible = true, binary = true)
    
    lazy val `opc-nodeset+xml`: MediaType = opcNodesetXml

    lazy val oscore: MediaType =
      MediaType("application", "oscore", compressible = false, binary = true)

    lazy val oxps: MediaType =
      MediaType("application", "oxps", compressible = false, binary = true, fileExtensions = List("oxps"))

    lazy val p21: MediaType =
      MediaType("application", "p21", compressible = false, binary = true)

    lazy val p21Zip: MediaType =
      MediaType("application", "p21+zip", compressible = false, binary = true)
    
    lazy val `p21+zip`: MediaType = p21Zip

    lazy val p2pOverlayXml: MediaType =
      MediaType("application", "p2p-overlay+xml", compressible = true, binary = true, fileExtensions = List("relo"))
    
    lazy val `p2p-overlay+xml`: MediaType = p2pOverlayXml

    lazy val parityfec: MediaType =
      MediaType("application", "parityfec", compressible = false, binary = true)

    lazy val passport: MediaType =
      MediaType("application", "passport", compressible = false, binary = true)

    lazy val patchOpsErrorXml: MediaType =
      MediaType("application", "patch-ops-error+xml", compressible = true, binary = true, fileExtensions = List("xer"))
    
    lazy val `patch-ops-error+xml`: MediaType = patchOpsErrorXml

    lazy val pdf: MediaType =
      MediaType("application", "pdf", compressible = false, binary = true, fileExtensions = List("pdf"))

    lazy val pdx: MediaType =
      MediaType("application", "pdx", compressible = false, binary = true)

    lazy val pemCertificateChain: MediaType =
      MediaType("application", "pem-certificate-chain", compressible = false, binary = true)
    
    lazy val `pem-certificate-chain`: MediaType = pemCertificateChain

    lazy val pgpEncrypted: MediaType =
      MediaType("application", "pgp-encrypted", compressible = false, binary = true, fileExtensions = List("pgp"))
    
    lazy val `pgp-encrypted`: MediaType = pgpEncrypted

    lazy val pgpKeys: MediaType =
      MediaType("application", "pgp-keys", compressible = false, binary = true, fileExtensions = List("asc"))
    
    lazy val `pgp-keys`: MediaType = pgpKeys

    lazy val pgpSignature: MediaType =
      MediaType("application", "pgp-signature", compressible = false, binary = true, fileExtensions = List("sig", "asc"))
    
    lazy val `pgp-signature`: MediaType = pgpSignature

    lazy val picsRules: MediaType =
      MediaType("application", "pics-rules", compressible = false, binary = true, fileExtensions = List("prf"))
    
    lazy val `pics-rules`: MediaType = picsRules

    lazy val pidfXml: MediaType =
      MediaType("application", "pidf+xml", compressible = true, binary = true)
    
    lazy val `pidf+xml`: MediaType = pidfXml

    lazy val pidfDiffXml: MediaType =
      MediaType("application", "pidf-diff+xml", compressible = true, binary = true)
    
    lazy val `pidf-diff+xml`: MediaType = pidfDiffXml

    lazy val pkcs10: MediaType =
      MediaType("application", "pkcs10", compressible = false, binary = true, fileExtensions = List("p10"))

    lazy val pkcs12: MediaType =
      MediaType("application", "pkcs12", compressible = false, binary = true)

    lazy val pkcs7Mime: MediaType =
      MediaType("application", "pkcs7-mime", compressible = false, binary = true, fileExtensions = List("p7m", "p7c"))
    
    lazy val `pkcs7-mime`: MediaType = pkcs7Mime

    lazy val pkcs7Signature: MediaType =
      MediaType("application", "pkcs7-signature", compressible = false, binary = true, fileExtensions = List("p7s"))
    
    lazy val `pkcs7-signature`: MediaType = pkcs7Signature

    lazy val pkcs8: MediaType =
      MediaType("application", "pkcs8", compressible = false, binary = true, fileExtensions = List("p8"))

    lazy val pkcs8Encrypted: MediaType =
      MediaType("application", "pkcs8-encrypted", compressible = false, binary = true)
    
    lazy val `pkcs8-encrypted`: MediaType = pkcs8Encrypted

    lazy val pkixAttrCert: MediaType =
      MediaType("application", "pkix-attr-cert", compressible = false, binary = true, fileExtensions = List("ac"))
    
    lazy val `pkix-attr-cert`: MediaType = pkixAttrCert

    lazy val pkixCert: MediaType =
      MediaType("application", "pkix-cert", compressible = false, binary = true, fileExtensions = List("cer"))
    
    lazy val `pkix-cert`: MediaType = pkixCert

    lazy val pkixCrl: MediaType =
      MediaType("application", "pkix-crl", compressible = false, binary = true, fileExtensions = List("crl"))
    
    lazy val `pkix-crl`: MediaType = pkixCrl

    lazy val pkixPkipath: MediaType =
      MediaType("application", "pkix-pkipath", compressible = false, binary = true, fileExtensions = List("pkipath"))
    
    lazy val `pkix-pkipath`: MediaType = pkixPkipath

    lazy val pkixcmp: MediaType =
      MediaType("application", "pkixcmp", compressible = false, binary = true, fileExtensions = List("pki"))

    lazy val plsXml: MediaType =
      MediaType("application", "pls+xml", compressible = true, binary = true, fileExtensions = List("pls"))
    
    lazy val `pls+xml`: MediaType = plsXml

    lazy val pocSettingsXml: MediaType =
      MediaType("application", "poc-settings+xml", compressible = true, binary = true)
    
    lazy val `poc-settings+xml`: MediaType = pocSettingsXml

    lazy val postscript: MediaType =
      MediaType("application", "postscript", compressible = true, binary = true, fileExtensions = List("ai", "eps", "ps"))

    lazy val ppspTrackerJson: MediaType =
      MediaType("application", "ppsp-tracker+json", compressible = true, binary = false)
    
    lazy val `ppsp-tracker+json`: MediaType = ppspTrackerJson

    lazy val privateTokenIssuerDirectory: MediaType =
      MediaType("application", "private-token-issuer-directory", compressible = false, binary = true)
    
    lazy val `private-token-issuer-directory`: MediaType = privateTokenIssuerDirectory

    lazy val privateTokenRequest: MediaType =
      MediaType("application", "private-token-request", compressible = false, binary = true)
    
    lazy val `private-token-request`: MediaType = privateTokenRequest

    lazy val privateTokenResponse: MediaType =
      MediaType("application", "private-token-response", compressible = false, binary = true)
    
    lazy val `private-token-response`: MediaType = privateTokenResponse

    lazy val problemJson: MediaType =
      MediaType("application", "problem+json", compressible = true, binary = false)
    
    lazy val `problem+json`: MediaType = problemJson

    lazy val problemXml: MediaType =
      MediaType("application", "problem+xml", compressible = true, binary = true)
    
    lazy val `problem+xml`: MediaType = problemXml

    lazy val protobuf: MediaType =
      MediaType("application", "protobuf", compressible = false, binary = true)

    lazy val protobufJson: MediaType =
      MediaType("application", "protobuf+json", compressible = true, binary = false)
    
    lazy val `protobuf+json`: MediaType = protobufJson

    lazy val provenanceXml: MediaType =
      MediaType("application", "provenance+xml", compressible = true, binary = true, fileExtensions = List("provx"))
    
    lazy val `provenance+xml`: MediaType = provenanceXml

    lazy val providedClaimsJwt: MediaType =
      MediaType("application", "provided-claims+jwt", compressible = false, binary = true)
    
    lazy val `provided-claims+jwt`: MediaType = providedClaimsJwt

    lazy val prsAlvestrandTitraxSheet: MediaType =
      MediaType("application", "prs.alvestrand.titrax-sheet", compressible = false, binary = true)
    
    lazy val `prs.alvestrand.titrax-sheet`: MediaType = prsAlvestrandTitraxSheet

    lazy val prsCww: MediaType =
      MediaType("application", "prs.cww", compressible = false, binary = true, fileExtensions = List("cww"))
    
    lazy val `prs.cww`: MediaType = prsCww

    lazy val prsCyn: MediaType =
      MediaType("application", "prs.cyn", compressible = false, binary = true)
    
    lazy val `prs.cyn`: MediaType = prsCyn

    lazy val prsHpubZip: MediaType =
      MediaType("application", "prs.hpub+zip", compressible = false, binary = true)
    
    lazy val `prs.hpub+zip`: MediaType = prsHpubZip

    lazy val prsImpliedDocumentXml: MediaType =
      MediaType("application", "prs.implied-document+xml", compressible = true, binary = true)
    
    lazy val `prs.implied-document+xml`: MediaType = prsImpliedDocumentXml

    lazy val prsImpliedExecutable: MediaType =
      MediaType("application", "prs.implied-executable", compressible = false, binary = true)
    
    lazy val `prs.implied-executable`: MediaType = prsImpliedExecutable

    lazy val prsImpliedObjectJson: MediaType =
      MediaType("application", "prs.implied-object+json", compressible = true, binary = false)
    
    lazy val `prs.implied-object+json`: MediaType = prsImpliedObjectJson

    lazy val prsImpliedObjectJsonSeq: MediaType =
      MediaType("application", "prs.implied-object+json-seq", compressible = false, binary = true)
    
    lazy val `prs.implied-object+json-seq`: MediaType = prsImpliedObjectJsonSeq

    lazy val prsImpliedObjectYaml: MediaType =
      MediaType("application", "prs.implied-object+yaml", compressible = false, binary = true)
    
    lazy val `prs.implied-object+yaml`: MediaType = prsImpliedObjectYaml

    lazy val prsImpliedStructure: MediaType =
      MediaType("application", "prs.implied-structure", compressible = false, binary = true)
    
    lazy val `prs.implied-structure`: MediaType = prsImpliedStructure

    lazy val prsMayfile: MediaType =
      MediaType("application", "prs.mayfile", compressible = false, binary = true)
    
    lazy val `prs.mayfile`: MediaType = prsMayfile

    lazy val prsNprend: MediaType =
      MediaType("application", "prs.nprend", compressible = false, binary = true)
    
    lazy val `prs.nprend`: MediaType = prsNprend

    lazy val prsPlucker: MediaType =
      MediaType("application", "prs.plucker", compressible = false, binary = true)
    
    lazy val `prs.plucker`: MediaType = prsPlucker

    lazy val prsRdfXmlCrypt: MediaType =
      MediaType("application", "prs.rdf-xml-crypt", compressible = false, binary = true)
    
    lazy val `prs.rdf-xml-crypt`: MediaType = prsRdfXmlCrypt

    lazy val prsSclt: MediaType =
      MediaType("application", "prs.sclt", compressible = false, binary = true)
    
    lazy val `prs.sclt`: MediaType = prsSclt

    lazy val prsVcfbzip2: MediaType =
      MediaType("application", "prs.vcfbzip2", compressible = false, binary = true)
    
    lazy val `prs.vcfbzip2`: MediaType = prsVcfbzip2

    lazy val prsXsfXml: MediaType =
      MediaType("application", "prs.xsf+xml", compressible = true, binary = true, fileExtensions = List("xsf"))
    
    lazy val `prs.xsf+xml`: MediaType = prsXsfXml

    lazy val pskcXml: MediaType =
      MediaType("application", "pskc+xml", compressible = true, binary = true, fileExtensions = List("pskcxml"))
    
    lazy val `pskc+xml`: MediaType = pskcXml

    lazy val pvdJson: MediaType =
      MediaType("application", "pvd+json", compressible = true, binary = false)
    
    lazy val `pvd+json`: MediaType = pvdJson

    lazy val qsig: MediaType =
      MediaType("application", "qsig", compressible = false, binary = true)

    lazy val ramlYaml: MediaType =
      MediaType("application", "raml+yaml", compressible = true, binary = true, fileExtensions = List("raml"))
    
    lazy val `raml+yaml`: MediaType = ramlYaml

    lazy val raptorfec: MediaType =
      MediaType("application", "raptorfec", compressible = false, binary = true)

    lazy val rdapJson: MediaType =
      MediaType("application", "rdap+json", compressible = true, binary = false)
    
    lazy val `rdap+json`: MediaType = rdapJson

    lazy val rdfXml: MediaType =
      MediaType("application", "rdf+xml", compressible = true, binary = true, fileExtensions = List("rdf", "owl"))
    
    lazy val `rdf+xml`: MediaType = rdfXml

    lazy val reginfoXml: MediaType =
      MediaType("application", "reginfo+xml", compressible = true, binary = true, fileExtensions = List("rif"))
    
    lazy val `reginfo+xml`: MediaType = reginfoXml

    lazy val relaxNgCompactSyntax: MediaType =
      MediaType("application", "relax-ng-compact-syntax", compressible = false, binary = true, fileExtensions = List("rnc"))
    
    lazy val `relax-ng-compact-syntax`: MediaType = relaxNgCompactSyntax

    lazy val remotePrinting: MediaType =
      MediaType("application", "remote-printing", compressible = false, binary = true)
    
    lazy val `remote-printing`: MediaType = remotePrinting

    lazy val reputonJson: MediaType =
      MediaType("application", "reputon+json", compressible = true, binary = false)
    
    lazy val `reputon+json`: MediaType = reputonJson

    lazy val resolveResponseJwt: MediaType =
      MediaType("application", "resolve-response+jwt", compressible = false, binary = true)
    
    lazy val `resolve-response+jwt`: MediaType = resolveResponseJwt

    lazy val resourceListsXml: MediaType =
      MediaType("application", "resource-lists+xml", compressible = true, binary = true, fileExtensions = List("rl"))
    
    lazy val `resource-lists+xml`: MediaType = resourceListsXml

    lazy val resourceListsDiffXml: MediaType =
      MediaType("application", "resource-lists-diff+xml", compressible = true, binary = true, fileExtensions = List("rld"))
    
    lazy val `resource-lists-diff+xml`: MediaType = resourceListsDiffXml

    lazy val rfcXml: MediaType =
      MediaType("application", "rfc+xml", compressible = true, binary = true)
    
    lazy val `rfc+xml`: MediaType = rfcXml

    lazy val riscos: MediaType =
      MediaType("application", "riscos", compressible = false, binary = true)

    lazy val rlmiXml: MediaType =
      MediaType("application", "rlmi+xml", compressible = true, binary = true)
    
    lazy val `rlmi+xml`: MediaType = rlmiXml

    lazy val rlsServicesXml: MediaType =
      MediaType("application", "rls-services+xml", compressible = true, binary = true, fileExtensions = List("rs"))
    
    lazy val `rls-services+xml`: MediaType = rlsServicesXml

    lazy val routeApdXml: MediaType =
      MediaType("application", "route-apd+xml", compressible = true, binary = true, fileExtensions = List("rapd"))
    
    lazy val `route-apd+xml`: MediaType = routeApdXml

    lazy val routeSTsidXml: MediaType =
      MediaType("application", "route-s-tsid+xml", compressible = true, binary = true, fileExtensions = List("sls"))
    
    lazy val `route-s-tsid+xml`: MediaType = routeSTsidXml

    lazy val routeUsdXml: MediaType =
      MediaType("application", "route-usd+xml", compressible = true, binary = true, fileExtensions = List("rusd"))
    
    lazy val `route-usd+xml`: MediaType = routeUsdXml

    lazy val rpkiChecklist: MediaType =
      MediaType("application", "rpki-checklist", compressible = false, binary = true)
    
    lazy val `rpki-checklist`: MediaType = rpkiChecklist

    lazy val rpkiGhostbusters: MediaType =
      MediaType("application", "rpki-ghostbusters", compressible = false, binary = true, fileExtensions = List("gbr"))
    
    lazy val `rpki-ghostbusters`: MediaType = rpkiGhostbusters

    lazy val rpkiManifest: MediaType =
      MediaType("application", "rpki-manifest", compressible = false, binary = true, fileExtensions = List("mft"))
    
    lazy val `rpki-manifest`: MediaType = rpkiManifest

    lazy val rpkiPublication: MediaType =
      MediaType("application", "rpki-publication", compressible = false, binary = true)
    
    lazy val `rpki-publication`: MediaType = rpkiPublication

    lazy val rpkiRoa: MediaType =
      MediaType("application", "rpki-roa", compressible = false, binary = true, fileExtensions = List("roa"))
    
    lazy val `rpki-roa`: MediaType = rpkiRoa

    lazy val rpkiSignedTal: MediaType =
      MediaType("application", "rpki-signed-tal", compressible = false, binary = true)
    
    lazy val `rpki-signed-tal`: MediaType = rpkiSignedTal

    lazy val rpkiUpdown: MediaType =
      MediaType("application", "rpki-updown", compressible = false, binary = true)
    
    lazy val `rpki-updown`: MediaType = rpkiUpdown

    lazy val rsMetadataXml: MediaType =
      MediaType("application", "rs-metadata+xml", compressible = true, binary = true)
    
    lazy val `rs-metadata+xml`: MediaType = rsMetadataXml

    lazy val rsdXml: MediaType =
      MediaType("application", "rsd+xml", compressible = true, binary = true, fileExtensions = List("rsd"))
    
    lazy val `rsd+xml`: MediaType = rsdXml

    lazy val rssXml: MediaType =
      MediaType("application", "rss+xml", compressible = true, binary = true, fileExtensions = List("rss"))
    
    lazy val `rss+xml`: MediaType = rssXml

    lazy val rtf: MediaType =
      MediaType("application", "rtf", compressible = true, binary = true, fileExtensions = List("rtf"))

    lazy val rtploopback: MediaType =
      MediaType("application", "rtploopback", compressible = false, binary = true)

    lazy val rtx: MediaType =
      MediaType("application", "rtx", compressible = false, binary = true)

    lazy val samlassertionXml: MediaType =
      MediaType("application", "samlassertion+xml", compressible = true, binary = true)
    
    lazy val `samlassertion+xml`: MediaType = samlassertionXml

    lazy val samlmetadataXml: MediaType =
      MediaType("application", "samlmetadata+xml", compressible = true, binary = true)
    
    lazy val `samlmetadata+xml`: MediaType = samlmetadataXml

    lazy val sarifJson: MediaType =
      MediaType("application", "sarif+json", compressible = true, binary = false)
    
    lazy val `sarif+json`: MediaType = sarifJson

    lazy val sarifExternalPropertiesJson: MediaType =
      MediaType("application", "sarif-external-properties+json", compressible = true, binary = false)
    
    lazy val `sarif-external-properties+json`: MediaType = sarifExternalPropertiesJson

    lazy val sbe: MediaType =
      MediaType("application", "sbe", compressible = false, binary = true)

    lazy val sbmlXml: MediaType =
      MediaType("application", "sbml+xml", compressible = true, binary = true, fileExtensions = List("sbml"))
    
    lazy val `sbml+xml`: MediaType = sbmlXml

    lazy val scaipXml: MediaType =
      MediaType("application", "scaip+xml", compressible = true, binary = true)
    
    lazy val `scaip+xml`: MediaType = scaipXml

    lazy val scimJson: MediaType =
      MediaType("application", "scim+json", compressible = true, binary = false)
    
    lazy val `scim+json`: MediaType = scimJson

    lazy val scittReceiptCose: MediaType =
      MediaType("application", "scitt-receipt+cose", compressible = false, binary = true)
    
    lazy val `scitt-receipt+cose`: MediaType = scittReceiptCose

    lazy val scittStatementCose: MediaType =
      MediaType("application", "scitt-statement+cose", compressible = false, binary = true)
    
    lazy val `scitt-statement+cose`: MediaType = scittStatementCose

    lazy val scvpCvRequest: MediaType =
      MediaType("application", "scvp-cv-request", compressible = false, binary = true, fileExtensions = List("scq"))
    
    lazy val `scvp-cv-request`: MediaType = scvpCvRequest

    lazy val scvpCvResponse: MediaType =
      MediaType("application", "scvp-cv-response", compressible = false, binary = true, fileExtensions = List("scs"))
    
    lazy val `scvp-cv-response`: MediaType = scvpCvResponse

    lazy val scvpVpRequest: MediaType =
      MediaType("application", "scvp-vp-request", compressible = false, binary = true, fileExtensions = List("spq"))
    
    lazy val `scvp-vp-request`: MediaType = scvpVpRequest

    lazy val scvpVpResponse: MediaType =
      MediaType("application", "scvp-vp-response", compressible = false, binary = true, fileExtensions = List("spp"))
    
    lazy val `scvp-vp-response`: MediaType = scvpVpResponse

    lazy val sdJwt: MediaType =
      MediaType("application", "sd-jwt", compressible = false, binary = true)
    
    lazy val `sd-jwt`: MediaType = sdJwt

    lazy val sdJwtJson: MediaType =
      MediaType("application", "sd-jwt+json", compressible = true, binary = false)
    
    lazy val `sd-jwt+json`: MediaType = sdJwtJson

    lazy val sdfJson: MediaType =
      MediaType("application", "sdf+json", compressible = true, binary = false)
    
    lazy val `sdf+json`: MediaType = sdfJson

    lazy val sdp: MediaType =
      MediaType("application", "sdp", compressible = false, binary = true, fileExtensions = List("sdp"))

    lazy val seceventJwt: MediaType =
      MediaType("application", "secevent+jwt", compressible = false, binary = true)
    
    lazy val `secevent+jwt`: MediaType = seceventJwt

    lazy val senmlCbor: MediaType =
      MediaType("application", "senml+cbor", compressible = false, binary = true)
    
    lazy val `senml+cbor`: MediaType = senmlCbor

    lazy val senmlJson: MediaType =
      MediaType("application", "senml+json", compressible = true, binary = false)
    
    lazy val `senml+json`: MediaType = senmlJson

    lazy val senmlXml: MediaType =
      MediaType("application", "senml+xml", compressible = true, binary = true, fileExtensions = List("senmlx"))
    
    lazy val `senml+xml`: MediaType = senmlXml

    lazy val senmlEtchCbor: MediaType =
      MediaType("application", "senml-etch+cbor", compressible = false, binary = true)
    
    lazy val `senml-etch+cbor`: MediaType = senmlEtchCbor

    lazy val senmlEtchJson: MediaType =
      MediaType("application", "senml-etch+json", compressible = true, binary = false)
    
    lazy val `senml-etch+json`: MediaType = senmlEtchJson

    lazy val senmlExi: MediaType =
      MediaType("application", "senml-exi", compressible = false, binary = true)
    
    lazy val `senml-exi`: MediaType = senmlExi

    lazy val sensmlCbor: MediaType =
      MediaType("application", "sensml+cbor", compressible = false, binary = true)
    
    lazy val `sensml+cbor`: MediaType = sensmlCbor

    lazy val sensmlJson: MediaType =
      MediaType("application", "sensml+json", compressible = true, binary = false)
    
    lazy val `sensml+json`: MediaType = sensmlJson

    lazy val sensmlXml: MediaType =
      MediaType("application", "sensml+xml", compressible = true, binary = true, fileExtensions = List("sensmlx"))
    
    lazy val `sensml+xml`: MediaType = sensmlXml

    lazy val sensmlExi: MediaType =
      MediaType("application", "sensml-exi", compressible = false, binary = true)
    
    lazy val `sensml-exi`: MediaType = sensmlExi

    lazy val sepXml: MediaType =
      MediaType("application", "sep+xml", compressible = true, binary = true)
    
    lazy val `sep+xml`: MediaType = sepXml

    lazy val sepExi: MediaType =
      MediaType("application", "sep-exi", compressible = false, binary = true)
    
    lazy val `sep-exi`: MediaType = sepExi

    lazy val sessionInfo: MediaType =
      MediaType("application", "session-info", compressible = false, binary = true)
    
    lazy val `session-info`: MediaType = sessionInfo

    lazy val setPayment: MediaType =
      MediaType("application", "set-payment", compressible = false, binary = true)
    
    lazy val `set-payment`: MediaType = setPayment

    lazy val setPaymentInitiation: MediaType =
      MediaType("application", "set-payment-initiation", compressible = false, binary = true, fileExtensions = List("setpay"))
    
    lazy val `set-payment-initiation`: MediaType = setPaymentInitiation

    lazy val setRegistration: MediaType =
      MediaType("application", "set-registration", compressible = false, binary = true)
    
    lazy val `set-registration`: MediaType = setRegistration

    lazy val setRegistrationInitiation: MediaType =
      MediaType("application", "set-registration-initiation", compressible = false, binary = true, fileExtensions = List("setreg"))
    
    lazy val `set-registration-initiation`: MediaType = setRegistrationInitiation

    lazy val sgml: MediaType =
      MediaType("application", "sgml", compressible = false, binary = true)

    lazy val sgmlOpenCatalog: MediaType =
      MediaType("application", "sgml-open-catalog", compressible = false, binary = true)
    
    lazy val `sgml-open-catalog`: MediaType = sgmlOpenCatalog

    lazy val shfXml: MediaType =
      MediaType("application", "shf+xml", compressible = true, binary = true, fileExtensions = List("shf"))
    
    lazy val `shf+xml`: MediaType = shfXml

    lazy val sieve: MediaType =
      MediaType("application", "sieve", compressible = false, binary = true, fileExtensions = List("siv", "sieve"))

    lazy val simpleFilterXml: MediaType =
      MediaType("application", "simple-filter+xml", compressible = true, binary = true)
    
    lazy val `simple-filter+xml`: MediaType = simpleFilterXml

    lazy val simpleMessageSummary: MediaType =
      MediaType("application", "simple-message-summary", compressible = false, binary = true)
    
    lazy val `simple-message-summary`: MediaType = simpleMessageSummary

    lazy val simplesymbolcontainer: MediaType =
      MediaType("application", "simplesymbolcontainer", compressible = false, binary = true)

    lazy val sipc: MediaType =
      MediaType("application", "sipc", compressible = false, binary = true)

    lazy val slate: MediaType =
      MediaType("application", "slate", compressible = false, binary = true)

    lazy val smil: MediaType =
      MediaType("application", "smil", compressible = false, binary = true)

    lazy val smilXml: MediaType =
      MediaType("application", "smil+xml", compressible = true, binary = true, fileExtensions = List("smi", "smil"))
    
    lazy val `smil+xml`: MediaType = smilXml

    lazy val smpte336m: MediaType =
      MediaType("application", "smpte336m", compressible = false, binary = true)

    lazy val soapFastinfoset: MediaType =
      MediaType("application", "soap+fastinfoset", compressible = false, binary = true)
    
    lazy val `soap+fastinfoset`: MediaType = soapFastinfoset

    lazy val soapXml: MediaType =
      MediaType("application", "soap+xml", compressible = true, binary = true)
    
    lazy val `soap+xml`: MediaType = soapXml

    lazy val sparqlQuery: MediaType =
      MediaType("application", "sparql-query", compressible = false, binary = true, fileExtensions = List("rq"))
    
    lazy val `sparql-query`: MediaType = sparqlQuery

    lazy val sparqlResultsXml: MediaType =
      MediaType("application", "sparql-results+xml", compressible = true, binary = true, fileExtensions = List("srx"))
    
    lazy val `sparql-results+xml`: MediaType = sparqlResultsXml

    lazy val spdxJson: MediaType =
      MediaType("application", "spdx+json", compressible = true, binary = false)
    
    lazy val `spdx+json`: MediaType = spdxJson

    lazy val spiritsEventXml: MediaType =
      MediaType("application", "spirits-event+xml", compressible = true, binary = true)
    
    lazy val `spirits-event+xml`: MediaType = spiritsEventXml

    lazy val sql: MediaType =
      MediaType("application", "sql", compressible = false, binary = true, fileExtensions = List("sql"))

    lazy val srgs: MediaType =
      MediaType("application", "srgs", compressible = false, binary = true, fileExtensions = List("gram"))

    lazy val srgsXml: MediaType =
      MediaType("application", "srgs+xml", compressible = true, binary = true, fileExtensions = List("grxml"))
    
    lazy val `srgs+xml`: MediaType = srgsXml

    lazy val sruXml: MediaType =
      MediaType("application", "sru+xml", compressible = true, binary = true, fileExtensions = List("sru"))
    
    lazy val `sru+xml`: MediaType = sruXml

    lazy val ssdlXml: MediaType =
      MediaType("application", "ssdl+xml", compressible = true, binary = true, fileExtensions = List("ssdl"))
    
    lazy val `ssdl+xml`: MediaType = ssdlXml

    lazy val sslkeylogfile: MediaType =
      MediaType("application", "sslkeylogfile", compressible = false, binary = true)

    lazy val ssmlXml: MediaType =
      MediaType("application", "ssml+xml", compressible = true, binary = true, fileExtensions = List("ssml"))
    
    lazy val `ssml+xml`: MediaType = ssmlXml

    lazy val st211041: MediaType =
      MediaType("application", "st2110-41", compressible = false, binary = true)
    
    lazy val `st2110-41`: MediaType = st211041

    lazy val stixJson: MediaType =
      MediaType("application", "stix+json", compressible = true, binary = false)
    
    lazy val `stix+json`: MediaType = stixJson

    lazy val stratum: MediaType =
      MediaType("application", "stratum", compressible = false, binary = true)

    lazy val suitEnvelopeCose: MediaType =
      MediaType("application", "suit-envelope+cose", compressible = false, binary = true)
    
    lazy val `suit-envelope+cose`: MediaType = suitEnvelopeCose

    lazy val suitReportCose: MediaType =
      MediaType("application", "suit-report+cose", compressible = false, binary = true)
    
    lazy val `suit-report+cose`: MediaType = suitReportCose

    lazy val swidCbor: MediaType =
      MediaType("application", "swid+cbor", compressible = false, binary = true)
    
    lazy val `swid+cbor`: MediaType = swidCbor

    lazy val swidXml: MediaType =
      MediaType("application", "swid+xml", compressible = true, binary = true, fileExtensions = List("swidtag"))
    
    lazy val `swid+xml`: MediaType = swidXml

    lazy val tampApexUpdate: MediaType =
      MediaType("application", "tamp-apex-update", compressible = false, binary = true)
    
    lazy val `tamp-apex-update`: MediaType = tampApexUpdate

    lazy val tampApexUpdateConfirm: MediaType =
      MediaType("application", "tamp-apex-update-confirm", compressible = false, binary = true)
    
    lazy val `tamp-apex-update-confirm`: MediaType = tampApexUpdateConfirm

    lazy val tampCommunityUpdate: MediaType =
      MediaType("application", "tamp-community-update", compressible = false, binary = true)
    
    lazy val `tamp-community-update`: MediaType = tampCommunityUpdate

    lazy val tampCommunityUpdateConfirm: MediaType =
      MediaType("application", "tamp-community-update-confirm", compressible = false, binary = true)
    
    lazy val `tamp-community-update-confirm`: MediaType = tampCommunityUpdateConfirm

    lazy val tampError: MediaType =
      MediaType("application", "tamp-error", compressible = false, binary = true)
    
    lazy val `tamp-error`: MediaType = tampError

    lazy val tampSequenceAdjust: MediaType =
      MediaType("application", "tamp-sequence-adjust", compressible = false, binary = true)
    
    lazy val `tamp-sequence-adjust`: MediaType = tampSequenceAdjust

    lazy val tampSequenceAdjustConfirm: MediaType =
      MediaType("application", "tamp-sequence-adjust-confirm", compressible = false, binary = true)
    
    lazy val `tamp-sequence-adjust-confirm`: MediaType = tampSequenceAdjustConfirm

    lazy val tampStatusQuery: MediaType =
      MediaType("application", "tamp-status-query", compressible = false, binary = true)
    
    lazy val `tamp-status-query`: MediaType = tampStatusQuery

    lazy val tampStatusResponse: MediaType =
      MediaType("application", "tamp-status-response", compressible = false, binary = true)
    
    lazy val `tamp-status-response`: MediaType = tampStatusResponse

    lazy val tampUpdate: MediaType =
      MediaType("application", "tamp-update", compressible = false, binary = true)
    
    lazy val `tamp-update`: MediaType = tampUpdate

    lazy val tampUpdateConfirm: MediaType =
      MediaType("application", "tamp-update-confirm", compressible = false, binary = true)
    
    lazy val `tamp-update-confirm`: MediaType = tampUpdateConfirm

    lazy val tar: MediaType =
      MediaType("application", "tar", compressible = true, binary = true)

    lazy val taxiiJson: MediaType =
      MediaType("application", "taxii+json", compressible = true, binary = false)
    
    lazy val `taxii+json`: MediaType = taxiiJson

    lazy val tdJson: MediaType =
      MediaType("application", "td+json", compressible = true, binary = false)
    
    lazy val `td+json`: MediaType = tdJson

    lazy val teiXml: MediaType =
      MediaType("application", "tei+xml", compressible = true, binary = true, fileExtensions = List("tei", "teicorpus"))
    
    lazy val `tei+xml`: MediaType = teiXml

    lazy val tetra_isi: MediaType =
      MediaType("application", "tetra_isi", compressible = false, binary = true)

    lazy val texinfo: MediaType =
      MediaType("application", "texinfo", compressible = false, binary = true)

    lazy val thraudXml: MediaType =
      MediaType("application", "thraud+xml", compressible = true, binary = true, fileExtensions = List("tfi"))
    
    lazy val `thraud+xml`: MediaType = thraudXml

    lazy val timestampQuery: MediaType =
      MediaType("application", "timestamp-query", compressible = false, binary = true)
    
    lazy val `timestamp-query`: MediaType = timestampQuery

    lazy val timestampReply: MediaType =
      MediaType("application", "timestamp-reply", compressible = false, binary = true)
    
    lazy val `timestamp-reply`: MediaType = timestampReply

    lazy val timestampedData: MediaType =
      MediaType("application", "timestamped-data", compressible = false, binary = true, fileExtensions = List("tsd"))
    
    lazy val `timestamped-data`: MediaType = timestampedData

    lazy val tlsrptGzip: MediaType =
      MediaType("application", "tlsrpt+gzip", compressible = false, binary = true)
    
    lazy val `tlsrpt+gzip`: MediaType = tlsrptGzip

    lazy val tlsrptJson: MediaType =
      MediaType("application", "tlsrpt+json", compressible = true, binary = false)
    
    lazy val `tlsrpt+json`: MediaType = tlsrptJson

    lazy val tmJson: MediaType =
      MediaType("application", "tm+json", compressible = true, binary = false)
    
    lazy val `tm+json`: MediaType = tmJson

    lazy val tnauthlist: MediaType =
      MediaType("application", "tnauthlist", compressible = false, binary = true)

    lazy val tocCbor: MediaType =
      MediaType("application", "toc+cbor", compressible = false, binary = true)
    
    lazy val `toc+cbor`: MediaType = tocCbor

    lazy val tokenIntrospectionJwt: MediaType =
      MediaType("application", "token-introspection+jwt", compressible = false, binary = true)
    
    lazy val `token-introspection+jwt`: MediaType = tokenIntrospectionJwt

    lazy val toml: MediaType =
      MediaType("application", "toml", compressible = true, binary = true, fileExtensions = List("toml"))

    lazy val trickleIceSdpfrag: MediaType =
      MediaType("application", "trickle-ice-sdpfrag", compressible = false, binary = true)
    
    lazy val `trickle-ice-sdpfrag`: MediaType = trickleIceSdpfrag

    lazy val trig: MediaType =
      MediaType("application", "trig", compressible = false, binary = true, fileExtensions = List("trig"))

    lazy val trustChainJson: MediaType =
      MediaType("application", "trust-chain+json", compressible = true, binary = false)
    
    lazy val `trust-chain+json`: MediaType = trustChainJson

    lazy val trustMarkJwt: MediaType =
      MediaType("application", "trust-mark+jwt", compressible = false, binary = true)
    
    lazy val `trust-mark+jwt`: MediaType = trustMarkJwt

    lazy val trustMarkDelegationJwt: MediaType =
      MediaType("application", "trust-mark-delegation+jwt", compressible = false, binary = true)
    
    lazy val `trust-mark-delegation+jwt`: MediaType = trustMarkDelegationJwt

    lazy val trustMarkStatusResponseJwt: MediaType =
      MediaType("application", "trust-mark-status-response+jwt", compressible = false, binary = true)
    
    lazy val `trust-mark-status-response+jwt`: MediaType = trustMarkStatusResponseJwt

    lazy val ttmlXml: MediaType =
      MediaType("application", "ttml+xml", compressible = true, binary = true, fileExtensions = List("ttml"))
    
    lazy val `ttml+xml`: MediaType = ttmlXml

    lazy val tveTrigger: MediaType =
      MediaType("application", "tve-trigger", compressible = false, binary = true)
    
    lazy val `tve-trigger`: MediaType = tveTrigger

    lazy val tzif: MediaType =
      MediaType("application", "tzif", compressible = false, binary = true)

    lazy val tzifLeap: MediaType =
      MediaType("application", "tzif-leap", compressible = false, binary = true)
    
    lazy val `tzif-leap`: MediaType = tzifLeap

    lazy val ubjson: MediaType =
      MediaType("application", "ubjson", compressible = false, binary = false, fileExtensions = List("ubj"))

    lazy val uccsCbor: MediaType =
      MediaType("application", "uccs+cbor", compressible = false, binary = true)
    
    lazy val `uccs+cbor`: MediaType = uccsCbor

    lazy val ujcsJson: MediaType =
      MediaType("application", "ujcs+json", compressible = true, binary = false)
    
    lazy val `ujcs+json`: MediaType = ujcsJson

    lazy val ulpfec: MediaType =
      MediaType("application", "ulpfec", compressible = false, binary = true)

    lazy val urcGrpsheetXml: MediaType =
      MediaType("application", "urc-grpsheet+xml", compressible = true, binary = true)
    
    lazy val `urc-grpsheet+xml`: MediaType = urcGrpsheetXml

    lazy val urcRessheetXml: MediaType =
      MediaType("application", "urc-ressheet+xml", compressible = true, binary = true, fileExtensions = List("rsheet"))
    
    lazy val `urc-ressheet+xml`: MediaType = urcRessheetXml

    lazy val urcTargetdescXml: MediaType =
      MediaType("application", "urc-targetdesc+xml", compressible = true, binary = true, fileExtensions = List("td"))
    
    lazy val `urc-targetdesc+xml`: MediaType = urcTargetdescXml

    lazy val urcUisocketdescXml: MediaType =
      MediaType("application", "urc-uisocketdesc+xml", compressible = true, binary = true)
    
    lazy val `urc-uisocketdesc+xml`: MediaType = urcUisocketdescXml

    lazy val vc: MediaType =
      MediaType("application", "vc", compressible = false, binary = true)

    lazy val vcCose: MediaType =
      MediaType("application", "vc+cose", compressible = false, binary = true)
    
    lazy val `vc+cose`: MediaType = vcCose

    lazy val vcJwt: MediaType =
      MediaType("application", "vc+jwt", compressible = false, binary = true)
    
    lazy val `vc+jwt`: MediaType = vcJwt

    lazy val vcSdJwt: MediaType =
      MediaType("application", "vc+sd-jwt", compressible = false, binary = true)
    
    lazy val `vc+sd-jwt`: MediaType = vcSdJwt

    lazy val vcardJson: MediaType =
      MediaType("application", "vcard+json", compressible = true, binary = false)
    
    lazy val `vcard+json`: MediaType = vcardJson

    lazy val vcardXml: MediaType =
      MediaType("application", "vcard+xml", compressible = true, binary = true)
    
    lazy val `vcard+xml`: MediaType = vcardXml

    lazy val vecXml: MediaType =
      MediaType("application", "vec+xml", compressible = true, binary = true, fileExtensions = List("vec"))
    
    lazy val `vec+xml`: MediaType = vecXml

    lazy val vecPackageGzip: MediaType =
      MediaType("application", "vec-package+gzip", compressible = false, binary = true)
    
    lazy val `vec-package+gzip`: MediaType = vecPackageGzip

    lazy val vecPackageZip: MediaType =
      MediaType("application", "vec-package+zip", compressible = false, binary = true)
    
    lazy val `vec-package+zip`: MediaType = vecPackageZip

    lazy val vemmi: MediaType =
      MediaType("application", "vemmi", compressible = false, binary = true)

    lazy val vividenceScriptfile: MediaType =
      MediaType("application", "vividence.scriptfile", compressible = false, binary = true)
    
    lazy val `vividence.scriptfile`: MediaType = vividenceScriptfile

    lazy val vnd1000mindsDecisionModelXml: MediaType =
      MediaType("application", "vnd.1000minds.decision-model+xml", compressible = true, binary = true, fileExtensions = List("1km"))
    
    lazy val `vnd.1000minds.decision-model+xml`: MediaType = vnd1000mindsDecisionModelXml

    lazy val vnd1ob: MediaType =
      MediaType("application", "vnd.1ob", compressible = false, binary = true)
    
    lazy val `vnd.1ob`: MediaType = vnd1ob

    lazy val vnd3gppProseXml: MediaType =
      MediaType("application", "vnd.3gpp-prose+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp-prose+xml`: MediaType = vnd3gppProseXml

    lazy val vnd3gppProsePc3aXml: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc3a+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp-prose-pc3a+xml`: MediaType = vnd3gppProsePc3aXml

    lazy val vnd3gppProsePc3achXml: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc3ach+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp-prose-pc3ach+xml`: MediaType = vnd3gppProsePc3achXml

    lazy val vnd3gppProsePc3chXml: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc3ch+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp-prose-pc3ch+xml`: MediaType = vnd3gppProsePc3chXml

    lazy val vnd3gppProsePc8Xml: MediaType =
      MediaType("application", "vnd.3gpp-prose-pc8+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp-prose-pc8+xml`: MediaType = vnd3gppProsePc8Xml

    lazy val vnd3gppV2xLocalServiceInformation: MediaType =
      MediaType("application", "vnd.3gpp-v2x-local-service-information", compressible = false, binary = true)
    
    lazy val `vnd.3gpp-v2x-local-service-information`: MediaType = vnd3gppV2xLocalServiceInformation

    lazy val vnd3gpp5gnas: MediaType =
      MediaType("application", "vnd.3gpp.5gnas", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.5gnas`: MediaType = vnd3gpp5gnas

    lazy val vnd3gpp5gsa2x: MediaType =
      MediaType("application", "vnd.3gpp.5gsa2x", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.5gsa2x`: MediaType = vnd3gpp5gsa2x

    lazy val vnd3gpp5gsa2xLocalServiceInformation: MediaType =
      MediaType("application", "vnd.3gpp.5gsa2x-local-service-information", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.5gsa2x-local-service-information`: MediaType = vnd3gpp5gsa2xLocalServiceInformation

    lazy val vnd3gpp5gsv2x: MediaType =
      MediaType("application", "vnd.3gpp.5gsv2x", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.5gsv2x`: MediaType = vnd3gpp5gsv2x

    lazy val vnd3gpp5gsv2xLocalServiceInformation: MediaType =
      MediaType("application", "vnd.3gpp.5gsv2x-local-service-information", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.5gsv2x-local-service-information`: MediaType = vnd3gpp5gsv2xLocalServiceInformation

    lazy val vnd3gppAccessTransferEventsXml: MediaType =
      MediaType("application", "vnd.3gpp.access-transfer-events+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.access-transfer-events+xml`: MediaType = vnd3gppAccessTransferEventsXml

    lazy val vnd3gppBsfXml: MediaType =
      MediaType("application", "vnd.3gpp.bsf+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.bsf+xml`: MediaType = vnd3gppBsfXml

    lazy val vnd3gppCrsXml: MediaType =
      MediaType("application", "vnd.3gpp.crs+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.crs+xml`: MediaType = vnd3gppCrsXml

    lazy val vnd3gppCurrentLocationDiscoveryXml: MediaType =
      MediaType("application", "vnd.3gpp.current-location-discovery+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.current-location-discovery+xml`: MediaType = vnd3gppCurrentLocationDiscoveryXml

    lazy val vnd3gppGmopXml: MediaType =
      MediaType("application", "vnd.3gpp.gmop+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.gmop+xml`: MediaType = vnd3gppGmopXml

    lazy val vnd3gppGtpc: MediaType =
      MediaType("application", "vnd.3gpp.gtpc", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.gtpc`: MediaType = vnd3gppGtpc

    lazy val vnd3gppInterworkingData: MediaType =
      MediaType("application", "vnd.3gpp.interworking-data", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.interworking-data`: MediaType = vnd3gppInterworkingData

    lazy val vnd3gppLpp: MediaType =
      MediaType("application", "vnd.3gpp.lpp", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.lpp`: MediaType = vnd3gppLpp

    lazy val vnd3gppMcSignallingEar: MediaType =
      MediaType("application", "vnd.3gpp.mc-signalling-ear", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.mc-signalling-ear`: MediaType = vnd3gppMcSignallingEar

    lazy val vnd3gppMcdataAffiliationCommandXml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-affiliation-command+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcdata-affiliation-command+xml`: MediaType = vnd3gppMcdataAffiliationCommandXml

    lazy val vnd3gppMcdataInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcdata-info+xml`: MediaType = vnd3gppMcdataInfoXml

    lazy val vnd3gppMcdataMsgstoreCtrlRequestXml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-msgstore-ctrl-request+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcdata-msgstore-ctrl-request+xml`: MediaType = vnd3gppMcdataMsgstoreCtrlRequestXml

    lazy val vnd3gppMcdataPayload: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-payload", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.mcdata-payload`: MediaType = vnd3gppMcdataPayload

    lazy val vnd3gppMcdataRegroupXml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-regroup+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcdata-regroup+xml`: MediaType = vnd3gppMcdataRegroupXml

    lazy val vnd3gppMcdataServiceConfigXml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-service-config+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcdata-service-config+xml`: MediaType = vnd3gppMcdataServiceConfigXml

    lazy val vnd3gppMcdataSignalling: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-signalling", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.mcdata-signalling`: MediaType = vnd3gppMcdataSignalling

    lazy val vnd3gppMcdataUeConfigXml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-ue-config+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcdata-ue-config+xml`: MediaType = vnd3gppMcdataUeConfigXml

    lazy val vnd3gppMcdataUserProfileXml: MediaType =
      MediaType("application", "vnd.3gpp.mcdata-user-profile+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcdata-user-profile+xml`: MediaType = vnd3gppMcdataUserProfileXml

    lazy val vnd3gppMcpttAffiliationCommandXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-affiliation-command+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-affiliation-command+xml`: MediaType = vnd3gppMcpttAffiliationCommandXml

    lazy val vnd3gppMcpttFloorRequestXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-floor-request+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-floor-request+xml`: MediaType = vnd3gppMcpttFloorRequestXml

    lazy val vnd3gppMcpttInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-info+xml`: MediaType = vnd3gppMcpttInfoXml

    lazy val vnd3gppMcpttLocationInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-location-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-location-info+xml`: MediaType = vnd3gppMcpttLocationInfoXml

    lazy val vnd3gppMcpttMbmsUsageInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-mbms-usage-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-mbms-usage-info+xml`: MediaType = vnd3gppMcpttMbmsUsageInfoXml

    lazy val vnd3gppMcpttRegroupXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-regroup+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-regroup+xml`: MediaType = vnd3gppMcpttRegroupXml

    lazy val vnd3gppMcpttServiceConfigXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-service-config+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-service-config+xml`: MediaType = vnd3gppMcpttServiceConfigXml

    lazy val vnd3gppMcpttSignedXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-signed+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-signed+xml`: MediaType = vnd3gppMcpttSignedXml

    lazy val vnd3gppMcpttUeConfigXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-ue-config+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-ue-config+xml`: MediaType = vnd3gppMcpttUeConfigXml

    lazy val vnd3gppMcpttUeInitConfigXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-ue-init-config+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-ue-init-config+xml`: MediaType = vnd3gppMcpttUeInitConfigXml

    lazy val vnd3gppMcpttUserProfileXml: MediaType =
      MediaType("application", "vnd.3gpp.mcptt-user-profile+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcptt-user-profile+xml`: MediaType = vnd3gppMcpttUserProfileXml

    lazy val vnd3gppMcvideoAffiliationCommandXml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-affiliation-command+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcvideo-affiliation-command+xml`: MediaType = vnd3gppMcvideoAffiliationCommandXml

    lazy val vnd3gppMcvideoInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcvideo-info+xml`: MediaType = vnd3gppMcvideoInfoXml

    lazy val vnd3gppMcvideoLocationInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-location-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcvideo-location-info+xml`: MediaType = vnd3gppMcvideoLocationInfoXml

    lazy val vnd3gppMcvideoMbmsUsageInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-mbms-usage-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcvideo-mbms-usage-info+xml`: MediaType = vnd3gppMcvideoMbmsUsageInfoXml

    lazy val vnd3gppMcvideoRegroupXml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-regroup+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcvideo-regroup+xml`: MediaType = vnd3gppMcvideoRegroupXml

    lazy val vnd3gppMcvideoServiceConfigXml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-service-config+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcvideo-service-config+xml`: MediaType = vnd3gppMcvideoServiceConfigXml

    lazy val vnd3gppMcvideoTransmissionRequestXml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-transmission-request+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcvideo-transmission-request+xml`: MediaType = vnd3gppMcvideoTransmissionRequestXml

    lazy val vnd3gppMcvideoUeConfigXml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-ue-config+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcvideo-ue-config+xml`: MediaType = vnd3gppMcvideoUeConfigXml

    lazy val vnd3gppMcvideoUserProfileXml: MediaType =
      MediaType("application", "vnd.3gpp.mcvideo-user-profile+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mcvideo-user-profile+xml`: MediaType = vnd3gppMcvideoUserProfileXml

    lazy val vnd3gppMidCallXml: MediaType =
      MediaType("application", "vnd.3gpp.mid-call+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.mid-call+xml`: MediaType = vnd3gppMidCallXml

    lazy val vnd3gppNgap: MediaType =
      MediaType("application", "vnd.3gpp.ngap", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.ngap`: MediaType = vnd3gppNgap

    lazy val vnd3gppPfcp: MediaType =
      MediaType("application", "vnd.3gpp.pfcp", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.pfcp`: MediaType = vnd3gppPfcp

    lazy val vnd3gppPicBwLarge: MediaType =
      MediaType("application", "vnd.3gpp.pic-bw-large", compressible = false, binary = true, fileExtensions = List("plb"))
    
    lazy val `vnd.3gpp.pic-bw-large`: MediaType = vnd3gppPicBwLarge

    lazy val vnd3gppPicBwSmall: MediaType =
      MediaType("application", "vnd.3gpp.pic-bw-small", compressible = false, binary = true, fileExtensions = List("psb"))
    
    lazy val `vnd.3gpp.pic-bw-small`: MediaType = vnd3gppPicBwSmall

    lazy val vnd3gppPicBwVar: MediaType =
      MediaType("application", "vnd.3gpp.pic-bw-var", compressible = false, binary = true, fileExtensions = List("pvb"))
    
    lazy val `vnd.3gpp.pic-bw-var`: MediaType = vnd3gppPicBwVar

    lazy val vnd3gppPinappInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.pinapp-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.pinapp-info+xml`: MediaType = vnd3gppPinappInfoXml

    lazy val vnd3gppS1ap: MediaType =
      MediaType("application", "vnd.3gpp.s1ap", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.s1ap`: MediaType = vnd3gppS1ap

    lazy val vnd3gppSealAppCommRequirementsInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-app-comm-requirements-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-app-comm-requirements-info+xml`: MediaType = vnd3gppSealAppCommRequirementsInfoXml

    lazy val vnd3gppSealDataDeliveryInfoCbor: MediaType =
      MediaType("application", "vnd.3gpp.seal-data-delivery-info+cbor", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.seal-data-delivery-info+cbor`: MediaType = vnd3gppSealDataDeliveryInfoCbor

    lazy val vnd3gppSealDataDeliveryInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-data-delivery-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-data-delivery-info+xml`: MediaType = vnd3gppSealDataDeliveryInfoXml

    lazy val vnd3gppSealGroupDocXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-group-doc+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-group-doc+xml`: MediaType = vnd3gppSealGroupDocXml

    lazy val vnd3gppSealInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-info+xml`: MediaType = vnd3gppSealInfoXml

    lazy val vnd3gppSealLocationInfoCbor: MediaType =
      MediaType("application", "vnd.3gpp.seal-location-info+cbor", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.seal-location-info+cbor`: MediaType = vnd3gppSealLocationInfoCbor

    lazy val vnd3gppSealLocationInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-location-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-location-info+xml`: MediaType = vnd3gppSealLocationInfoXml

    lazy val vnd3gppSealMbmsUsageInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-mbms-usage-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-mbms-usage-info+xml`: MediaType = vnd3gppSealMbmsUsageInfoXml

    lazy val vnd3gppSealMbsUsageInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-mbs-usage-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-mbs-usage-info+xml`: MediaType = vnd3gppSealMbsUsageInfoXml

    lazy val vnd3gppSealNetworkQosManagementInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-network-qos-management-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-network-qos-management-info+xml`: MediaType = vnd3gppSealNetworkQosManagementInfoXml

    lazy val vnd3gppSealNetworkResourceInfoCbor: MediaType =
      MediaType("application", "vnd.3gpp.seal-network-resource-info+cbor", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.seal-network-resource-info+cbor`: MediaType = vnd3gppSealNetworkResourceInfoCbor

    lazy val vnd3gppSealUeConfigInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-ue-config-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-ue-config-info+xml`: MediaType = vnd3gppSealUeConfigInfoXml

    lazy val vnd3gppSealUnicastInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-unicast-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-unicast-info+xml`: MediaType = vnd3gppSealUnicastInfoXml

    lazy val vnd3gppSealUserProfileInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.seal-user-profile-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.seal-user-profile-info+xml`: MediaType = vnd3gppSealUserProfileInfoXml

    lazy val vnd3gppSms: MediaType =
      MediaType("application", "vnd.3gpp.sms", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.sms`: MediaType = vnd3gppSms

    lazy val vnd3gppSmsXml: MediaType =
      MediaType("application", "vnd.3gpp.sms+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.sms+xml`: MediaType = vnd3gppSmsXml

    lazy val vnd3gppSrvccExtXml: MediaType =
      MediaType("application", "vnd.3gpp.srvcc-ext+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.srvcc-ext+xml`: MediaType = vnd3gppSrvccExtXml

    lazy val vnd3gppSrvccInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.srvcc-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.srvcc-info+xml`: MediaType = vnd3gppSrvccInfoXml

    lazy val vnd3gppStateAndEventInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.state-and-event-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.state-and-event-info+xml`: MediaType = vnd3gppStateAndEventInfoXml

    lazy val vnd3gppUssdXml: MediaType =
      MediaType("application", "vnd.3gpp.ussd+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.ussd+xml`: MediaType = vnd3gppUssdXml

    lazy val vnd3gppV2x: MediaType =
      MediaType("application", "vnd.3gpp.v2x", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.v2x`: MediaType = vnd3gppV2x

    lazy val vnd3gppVaeInfoXml: MediaType =
      MediaType("application", "vnd.3gpp.vae-info+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp.vae-info+xml`: MediaType = vnd3gppVaeInfoXml

    lazy val vnd3gpp2BcmcsinfoXml: MediaType =
      MediaType("application", "vnd.3gpp2.bcmcsinfo+xml", compressible = true, binary = true)
    
    lazy val `vnd.3gpp2.bcmcsinfo+xml`: MediaType = vnd3gpp2BcmcsinfoXml

    lazy val vnd3gpp2Sms: MediaType =
      MediaType("application", "vnd.3gpp2.sms", compressible = false, binary = true)
    
    lazy val `vnd.3gpp2.sms`: MediaType = vnd3gpp2Sms

    lazy val vnd3gpp2Tcap: MediaType =
      MediaType("application", "vnd.3gpp2.tcap", compressible = false, binary = true, fileExtensions = List("tcap"))
    
    lazy val `vnd.3gpp2.tcap`: MediaType = vnd3gpp2Tcap

    lazy val vnd3lightssoftwareImagescal: MediaType =
      MediaType("application", "vnd.3lightssoftware.imagescal", compressible = false, binary = true)
    
    lazy val `vnd.3lightssoftware.imagescal`: MediaType = vnd3lightssoftwareImagescal

    lazy val vnd3mPostItNotes: MediaType =
      MediaType("application", "vnd.3m.post-it-notes", compressible = false, binary = true, fileExtensions = List("pwn"))
    
    lazy val `vnd.3m.post-it-notes`: MediaType = vnd3mPostItNotes

    lazy val vndAccpacSimplyAso: MediaType =
      MediaType("application", "vnd.accpac.simply.aso", compressible = false, binary = true, fileExtensions = List("aso"))
    
    lazy val `vnd.accpac.simply.aso`: MediaType = vndAccpacSimplyAso

    lazy val vndAccpacSimplyImp: MediaType =
      MediaType("application", "vnd.accpac.simply.imp", compressible = false, binary = true, fileExtensions = List("imp"))
    
    lazy val `vnd.accpac.simply.imp`: MediaType = vndAccpacSimplyImp

    lazy val vndAcmAddressxferJson: MediaType =
      MediaType("application", "vnd.acm.addressxfer+json", compressible = true, binary = false)
    
    lazy val `vnd.acm.addressxfer+json`: MediaType = vndAcmAddressxferJson

    lazy val vndAcmChatbotJson: MediaType =
      MediaType("application", "vnd.acm.chatbot+json", compressible = true, binary = false)
    
    lazy val `vnd.acm.chatbot+json`: MediaType = vndAcmChatbotJson

    lazy val vndAcucobol: MediaType =
      MediaType("application", "vnd.acucobol", compressible = false, binary = true, fileExtensions = List("acu"))
    
    lazy val `vnd.acucobol`: MediaType = vndAcucobol

    lazy val vndAcucorp: MediaType =
      MediaType("application", "vnd.acucorp", compressible = false, binary = true, fileExtensions = List("atc", "acutc"))
    
    lazy val `vnd.acucorp`: MediaType = vndAcucorp

    lazy val vndAdobeAirApplicationInstallerPackageZip: MediaType =
      MediaType("application", "vnd.adobe.air-application-installer-package+zip", compressible = false, binary = true, fileExtensions = List("air"))
    
    lazy val `vnd.adobe.air-application-installer-package+zip`: MediaType = vndAdobeAirApplicationInstallerPackageZip

    lazy val vndAdobeFlashMovie: MediaType =
      MediaType("application", "vnd.adobe.flash.movie", compressible = false, binary = true)
    
    lazy val `vnd.adobe.flash.movie`: MediaType = vndAdobeFlashMovie

    lazy val vndAdobeFormscentralFcdt: MediaType =
      MediaType("application", "vnd.adobe.formscentral.fcdt", compressible = false, binary = true, fileExtensions = List("fcdt"))
    
    lazy val `vnd.adobe.formscentral.fcdt`: MediaType = vndAdobeFormscentralFcdt

    lazy val vndAdobeFxp: MediaType =
      MediaType("application", "vnd.adobe.fxp", compressible = false, binary = true, fileExtensions = List("fxp", "fxpl"))
    
    lazy val `vnd.adobe.fxp`: MediaType = vndAdobeFxp

    lazy val vndAdobePartialUpload: MediaType =
      MediaType("application", "vnd.adobe.partial-upload", compressible = false, binary = true)
    
    lazy val `vnd.adobe.partial-upload`: MediaType = vndAdobePartialUpload

    lazy val vndAdobeXdpXml: MediaType =
      MediaType("application", "vnd.adobe.xdp+xml", compressible = true, binary = true, fileExtensions = List("xdp"))
    
    lazy val `vnd.adobe.xdp+xml`: MediaType = vndAdobeXdpXml

    lazy val vndAdobeXfdf: MediaType =
      MediaType("application", "vnd.adobe.xfdf", compressible = false, binary = true, fileExtensions = List("xfdf"))
    
    lazy val `vnd.adobe.xfdf`: MediaType = vndAdobeXfdf

    lazy val vndAetherImp: MediaType =
      MediaType("application", "vnd.aether.imp", compressible = false, binary = true)
    
    lazy val `vnd.aether.imp`: MediaType = vndAetherImp

    lazy val vndAfpcAfplinedata: MediaType =
      MediaType("application", "vnd.afpc.afplinedata", compressible = false, binary = true)
    
    lazy val `vnd.afpc.afplinedata`: MediaType = vndAfpcAfplinedata

    lazy val vndAfpcAfplinedataPagedef: MediaType =
      MediaType("application", "vnd.afpc.afplinedata-pagedef", compressible = false, binary = true)
    
    lazy val `vnd.afpc.afplinedata-pagedef`: MediaType = vndAfpcAfplinedataPagedef

    lazy val vndAfpcCmocaCmresource: MediaType =
      MediaType("application", "vnd.afpc.cmoca-cmresource", compressible = false, binary = true)
    
    lazy val `vnd.afpc.cmoca-cmresource`: MediaType = vndAfpcCmocaCmresource

    lazy val vndAfpcFocaCharset: MediaType =
      MediaType("application", "vnd.afpc.foca-charset", compressible = false, binary = true)
    
    lazy val `vnd.afpc.foca-charset`: MediaType = vndAfpcFocaCharset

    lazy val vndAfpcFocaCodedfont: MediaType =
      MediaType("application", "vnd.afpc.foca-codedfont", compressible = false, binary = true)
    
    lazy val `vnd.afpc.foca-codedfont`: MediaType = vndAfpcFocaCodedfont

    lazy val vndAfpcFocaCodepage: MediaType =
      MediaType("application", "vnd.afpc.foca-codepage", compressible = false, binary = true)
    
    lazy val `vnd.afpc.foca-codepage`: MediaType = vndAfpcFocaCodepage

    lazy val vndAfpcModca: MediaType =
      MediaType("application", "vnd.afpc.modca", compressible = false, binary = true)
    
    lazy val `vnd.afpc.modca`: MediaType = vndAfpcModca

    lazy val vndAfpcModcaCmtable: MediaType =
      MediaType("application", "vnd.afpc.modca-cmtable", compressible = false, binary = true)
    
    lazy val `vnd.afpc.modca-cmtable`: MediaType = vndAfpcModcaCmtable

    lazy val vndAfpcModcaFormdef: MediaType =
      MediaType("application", "vnd.afpc.modca-formdef", compressible = false, binary = true)
    
    lazy val `vnd.afpc.modca-formdef`: MediaType = vndAfpcModcaFormdef

    lazy val vndAfpcModcaMediummap: MediaType =
      MediaType("application", "vnd.afpc.modca-mediummap", compressible = false, binary = true)
    
    lazy val `vnd.afpc.modca-mediummap`: MediaType = vndAfpcModcaMediummap

    lazy val vndAfpcModcaObjectcontainer: MediaType =
      MediaType("application", "vnd.afpc.modca-objectcontainer", compressible = false, binary = true)
    
    lazy val `vnd.afpc.modca-objectcontainer`: MediaType = vndAfpcModcaObjectcontainer

    lazy val vndAfpcModcaOverlay: MediaType =
      MediaType("application", "vnd.afpc.modca-overlay", compressible = false, binary = true)
    
    lazy val `vnd.afpc.modca-overlay`: MediaType = vndAfpcModcaOverlay

    lazy val vndAfpcModcaPagesegment: MediaType =
      MediaType("application", "vnd.afpc.modca-pagesegment", compressible = false, binary = true)
    
    lazy val `vnd.afpc.modca-pagesegment`: MediaType = vndAfpcModcaPagesegment

    lazy val vndAge: MediaType =
      MediaType("application", "vnd.age", compressible = false, binary = true, fileExtensions = List("age"))
    
    lazy val `vnd.age`: MediaType = vndAge

    lazy val vndAhBarcode: MediaType =
      MediaType("application", "vnd.ah-barcode", compressible = false, binary = true)
    
    lazy val `vnd.ah-barcode`: MediaType = vndAhBarcode

    lazy val vndAheadSpace: MediaType =
      MediaType("application", "vnd.ahead.space", compressible = false, binary = true, fileExtensions = List("ahead"))
    
    lazy val `vnd.ahead.space`: MediaType = vndAheadSpace

    lazy val vndAia: MediaType =
      MediaType("application", "vnd.aia", compressible = false, binary = true)
    
    lazy val `vnd.aia`: MediaType = vndAia

    lazy val vndAirzipFilesecureAzf: MediaType =
      MediaType("application", "vnd.airzip.filesecure.azf", compressible = false, binary = true, fileExtensions = List("azf"))
    
    lazy val `vnd.airzip.filesecure.azf`: MediaType = vndAirzipFilesecureAzf

    lazy val vndAirzipFilesecureAzs: MediaType =
      MediaType("application", "vnd.airzip.filesecure.azs", compressible = false, binary = true, fileExtensions = List("azs"))
    
    lazy val `vnd.airzip.filesecure.azs`: MediaType = vndAirzipFilesecureAzs

    lazy val vndAmadeusJson: MediaType =
      MediaType("application", "vnd.amadeus+json", compressible = true, binary = false)
    
    lazy val `vnd.amadeus+json`: MediaType = vndAmadeusJson

    lazy val vndAmazonEbook: MediaType =
      MediaType("application", "vnd.amazon.ebook", compressible = false, binary = true, fileExtensions = List("azw"))
    
    lazy val `vnd.amazon.ebook`: MediaType = vndAmazonEbook

    lazy val vndAmazonMobi8Ebook: MediaType =
      MediaType("application", "vnd.amazon.mobi8-ebook", compressible = false, binary = true)
    
    lazy val `vnd.amazon.mobi8-ebook`: MediaType = vndAmazonMobi8Ebook

    lazy val vndAmericandynamicsAcc: MediaType =
      MediaType("application", "vnd.americandynamics.acc", compressible = false, binary = true, fileExtensions = List("acc"))
    
    lazy val `vnd.americandynamics.acc`: MediaType = vndAmericandynamicsAcc

    lazy val vndAmigaAmi: MediaType =
      MediaType("application", "vnd.amiga.ami", compressible = false, binary = true, fileExtensions = List("ami"))
    
    lazy val `vnd.amiga.ami`: MediaType = vndAmigaAmi

    lazy val vndAmundsenMazeXml: MediaType =
      MediaType("application", "vnd.amundsen.maze+xml", compressible = true, binary = true)
    
    lazy val `vnd.amundsen.maze+xml`: MediaType = vndAmundsenMazeXml

    lazy val vndAndroidOta: MediaType =
      MediaType("application", "vnd.android.ota", compressible = false, binary = true)
    
    lazy val `vnd.android.ota`: MediaType = vndAndroidOta

    lazy val vndAndroidPackageArchive: MediaType =
      MediaType("application", "vnd.android.package-archive", compressible = false, binary = true, fileExtensions = List("apk"))
    
    lazy val `vnd.android.package-archive`: MediaType = vndAndroidPackageArchive

    lazy val vndAnki: MediaType =
      MediaType("application", "vnd.anki", compressible = false, binary = true)
    
    lazy val `vnd.anki`: MediaType = vndAnki

    lazy val vndAnserWebCertificateIssueInitiation: MediaType =
      MediaType("application", "vnd.anser-web-certificate-issue-initiation", compressible = false, binary = true, fileExtensions = List("cii"))
    
    lazy val `vnd.anser-web-certificate-issue-initiation`: MediaType = vndAnserWebCertificateIssueInitiation

    lazy val vndAnserWebFundsTransferInitiation: MediaType =
      MediaType("application", "vnd.anser-web-funds-transfer-initiation", compressible = false, binary = true, fileExtensions = List("fti"))
    
    lazy val `vnd.anser-web-funds-transfer-initiation`: MediaType = vndAnserWebFundsTransferInitiation

    lazy val vndAntixGameComponent: MediaType =
      MediaType("application", "vnd.antix.game-component", compressible = false, binary = true, fileExtensions = List("atx"))
    
    lazy val `vnd.antix.game-component`: MediaType = vndAntixGameComponent

    lazy val vndApacheArrowFile: MediaType =
      MediaType("application", "vnd.apache.arrow.file", compressible = false, binary = true)
    
    lazy val `vnd.apache.arrow.file`: MediaType = vndApacheArrowFile

    lazy val vndApacheArrowStream: MediaType =
      MediaType("application", "vnd.apache.arrow.stream", compressible = false, binary = true)
    
    lazy val `vnd.apache.arrow.stream`: MediaType = vndApacheArrowStream

    lazy val vndApacheParquet: MediaType =
      MediaType("application", "vnd.apache.parquet", compressible = false, binary = true, fileExtensions = List("parquet"))
    
    lazy val `vnd.apache.parquet`: MediaType = vndApacheParquet

    lazy val vndApacheThriftBinary: MediaType =
      MediaType("application", "vnd.apache.thrift.binary", compressible = false, binary = true)
    
    lazy val `vnd.apache.thrift.binary`: MediaType = vndApacheThriftBinary

    lazy val vndApacheThriftCompact: MediaType =
      MediaType("application", "vnd.apache.thrift.compact", compressible = false, binary = true)
    
    lazy val `vnd.apache.thrift.compact`: MediaType = vndApacheThriftCompact

    lazy val vndApacheThriftJson: MediaType =
      MediaType("application", "vnd.apache.thrift.json", compressible = false, binary = false)
    
    lazy val `vnd.apache.thrift.json`: MediaType = vndApacheThriftJson

    lazy val vndApexlang: MediaType =
      MediaType("application", "vnd.apexlang", compressible = false, binary = true)
    
    lazy val `vnd.apexlang`: MediaType = vndApexlang

    lazy val vndApiJson: MediaType =
      MediaType("application", "vnd.api+json", compressible = true, binary = false)
    
    lazy val `vnd.api+json`: MediaType = vndApiJson

    lazy val vndAplextorWarrpJson: MediaType =
      MediaType("application", "vnd.aplextor.warrp+json", compressible = true, binary = false)
    
    lazy val `vnd.aplextor.warrp+json`: MediaType = vndAplextorWarrpJson

    lazy val vndApothekendeReservationJson: MediaType =
      MediaType("application", "vnd.apothekende.reservation+json", compressible = true, binary = false)
    
    lazy val `vnd.apothekende.reservation+json`: MediaType = vndApothekendeReservationJson

    lazy val vndAppleInstallerXml: MediaType =
      MediaType("application", "vnd.apple.installer+xml", compressible = true, binary = true, fileExtensions = List("mpkg"))
    
    lazy val `vnd.apple.installer+xml`: MediaType = vndAppleInstallerXml

    lazy val vndAppleKeynote: MediaType =
      MediaType("application", "vnd.apple.keynote", compressible = false, binary = true, fileExtensions = List("key"))
    
    lazy val `vnd.apple.keynote`: MediaType = vndAppleKeynote

    lazy val vndAppleMpegurl: MediaType =
      MediaType("application", "vnd.apple.mpegurl", compressible = false, binary = true, fileExtensions = List("m3u8"))
    
    lazy val `vnd.apple.mpegurl`: MediaType = vndAppleMpegurl

    lazy val vndAppleNumbers: MediaType =
      MediaType("application", "vnd.apple.numbers", compressible = false, binary = true, fileExtensions = List("numbers"))
    
    lazy val `vnd.apple.numbers`: MediaType = vndAppleNumbers

    lazy val vndApplePages: MediaType =
      MediaType("application", "vnd.apple.pages", compressible = false, binary = true, fileExtensions = List("pages"))
    
    lazy val `vnd.apple.pages`: MediaType = vndApplePages

    lazy val vndApplePkpass: MediaType =
      MediaType("application", "vnd.apple.pkpass", compressible = false, binary = true, fileExtensions = List("pkpass"))
    
    lazy val `vnd.apple.pkpass`: MediaType = vndApplePkpass

    lazy val vndArastraSwi: MediaType =
      MediaType("application", "vnd.arastra.swi", compressible = false, binary = true)
    
    lazy val `vnd.arastra.swi`: MediaType = vndArastraSwi

    lazy val vndAristanetworksSwi: MediaType =
      MediaType("application", "vnd.aristanetworks.swi", compressible = false, binary = true, fileExtensions = List("swi"))
    
    lazy val `vnd.aristanetworks.swi`: MediaType = vndAristanetworksSwi

    lazy val vndArtisanJson: MediaType =
      MediaType("application", "vnd.artisan+json", compressible = true, binary = false)
    
    lazy val `vnd.artisan+json`: MediaType = vndArtisanJson

    lazy val vndArtsquare: MediaType =
      MediaType("application", "vnd.artsquare", compressible = false, binary = true)
    
    lazy val `vnd.artsquare`: MediaType = vndArtsquare

    lazy val vndAs207960VasConfigJer: MediaType =
      MediaType("application", "vnd.as207960.vas.config+jer", compressible = false, binary = true)
    
    lazy val `vnd.as207960.vas.config+jer`: MediaType = vndAs207960VasConfigJer

    lazy val vndAs207960VasConfigUper: MediaType =
      MediaType("application", "vnd.as207960.vas.config+uper", compressible = false, binary = true)
    
    lazy val `vnd.as207960.vas.config+uper`: MediaType = vndAs207960VasConfigUper

    lazy val vndAs207960VasTapJer: MediaType =
      MediaType("application", "vnd.as207960.vas.tap+jer", compressible = false, binary = true)
    
    lazy val `vnd.as207960.vas.tap+jer`: MediaType = vndAs207960VasTapJer

    lazy val vndAs207960VasTapUper: MediaType =
      MediaType("application", "vnd.as207960.vas.tap+uper", compressible = false, binary = true)
    
    lazy val `vnd.as207960.vas.tap+uper`: MediaType = vndAs207960VasTapUper

    lazy val vndAstraeaSoftwareIota: MediaType =
      MediaType("application", "vnd.astraea-software.iota", compressible = false, binary = true, fileExtensions = List("iota"))
    
    lazy val `vnd.astraea-software.iota`: MediaType = vndAstraeaSoftwareIota

    lazy val vndAudiograph: MediaType =
      MediaType("application", "vnd.audiograph", compressible = false, binary = true, fileExtensions = List("aep"))
    
    lazy val `vnd.audiograph`: MediaType = vndAudiograph

    lazy val vndAutodeskFbx: MediaType =
      MediaType("application", "vnd.autodesk.fbx", compressible = false, binary = true, fileExtensions = List("fbx"))
    
    lazy val `vnd.autodesk.fbx`: MediaType = vndAutodeskFbx

    lazy val vndAutopackage: MediaType =
      MediaType("application", "vnd.autopackage", compressible = false, binary = true)
    
    lazy val `vnd.autopackage`: MediaType = vndAutopackage

    lazy val vndAvalonJson: MediaType =
      MediaType("application", "vnd.avalon+json", compressible = true, binary = false)
    
    lazy val `vnd.avalon+json`: MediaType = vndAvalonJson

    lazy val vndAvistarXml: MediaType =
      MediaType("application", "vnd.avistar+xml", compressible = true, binary = true)
    
    lazy val `vnd.avistar+xml`: MediaType = vndAvistarXml

    lazy val vndBalsamiqBmmlXml: MediaType =
      MediaType("application", "vnd.balsamiq.bmml+xml", compressible = true, binary = true, fileExtensions = List("bmml"))
    
    lazy val `vnd.balsamiq.bmml+xml`: MediaType = vndBalsamiqBmmlXml

    lazy val vndBalsamiqBmpr: MediaType =
      MediaType("application", "vnd.balsamiq.bmpr", compressible = false, binary = true)
    
    lazy val `vnd.balsamiq.bmpr`: MediaType = vndBalsamiqBmpr

    lazy val vndBananaAccounting: MediaType =
      MediaType("application", "vnd.banana-accounting", compressible = false, binary = true)
    
    lazy val `vnd.banana-accounting`: MediaType = vndBananaAccounting

    lazy val vndBbfUspError: MediaType =
      MediaType("application", "vnd.bbf.usp.error", compressible = false, binary = true)
    
    lazy val `vnd.bbf.usp.error`: MediaType = vndBbfUspError

    lazy val vndBbfUspMsg: MediaType =
      MediaType("application", "vnd.bbf.usp.msg", compressible = false, binary = true)
    
    lazy val `vnd.bbf.usp.msg`: MediaType = vndBbfUspMsg

    lazy val vndBbfUspMsgJson: MediaType =
      MediaType("application", "vnd.bbf.usp.msg+json", compressible = true, binary = false)
    
    lazy val `vnd.bbf.usp.msg+json`: MediaType = vndBbfUspMsgJson

    lazy val vndBekitzurStechJson: MediaType =
      MediaType("application", "vnd.bekitzur-stech+json", compressible = true, binary = false)
    
    lazy val `vnd.bekitzur-stech+json`: MediaType = vndBekitzurStechJson

    lazy val vndBelightsoftLhzdZip: MediaType =
      MediaType("application", "vnd.belightsoft.lhzd+zip", compressible = false, binary = true)
    
    lazy val `vnd.belightsoft.lhzd+zip`: MediaType = vndBelightsoftLhzdZip

    lazy val vndBelightsoftLhzlZip: MediaType =
      MediaType("application", "vnd.belightsoft.lhzl+zip", compressible = false, binary = true)
    
    lazy val `vnd.belightsoft.lhzl+zip`: MediaType = vndBelightsoftLhzlZip

    lazy val vndBintMedContent: MediaType =
      MediaType("application", "vnd.bint.med-content", compressible = false, binary = true)
    
    lazy val `vnd.bint.med-content`: MediaType = vndBintMedContent

    lazy val vndBiopaxRdfXml: MediaType =
      MediaType("application", "vnd.biopax.rdf+xml", compressible = true, binary = true)
    
    lazy val `vnd.biopax.rdf+xml`: MediaType = vndBiopaxRdfXml

    lazy val vndBlinkIdbValueWrapper: MediaType =
      MediaType("application", "vnd.blink-idb-value-wrapper", compressible = false, binary = true)
    
    lazy val `vnd.blink-idb-value-wrapper`: MediaType = vndBlinkIdbValueWrapper

    lazy val vndBlueiceMultipass: MediaType =
      MediaType("application", "vnd.blueice.multipass", compressible = false, binary = true, fileExtensions = List("mpm"))
    
    lazy val `vnd.blueice.multipass`: MediaType = vndBlueiceMultipass

    lazy val vndBluetoothEpOob: MediaType =
      MediaType("application", "vnd.bluetooth.ep.oob", compressible = false, binary = true)
    
    lazy val `vnd.bluetooth.ep.oob`: MediaType = vndBluetoothEpOob

    lazy val vndBluetoothLeOob: MediaType =
      MediaType("application", "vnd.bluetooth.le.oob", compressible = false, binary = true)
    
    lazy val `vnd.bluetooth.le.oob`: MediaType = vndBluetoothLeOob

    lazy val vndBmi: MediaType =
      MediaType("application", "vnd.bmi", compressible = false, binary = true, fileExtensions = List("bmi"))
    
    lazy val `vnd.bmi`: MediaType = vndBmi

    lazy val vndBpf: MediaType =
      MediaType("application", "vnd.bpf", compressible = false, binary = true)
    
    lazy val `vnd.bpf`: MediaType = vndBpf

    lazy val vndBpf3: MediaType =
      MediaType("application", "vnd.bpf3", compressible = false, binary = true)
    
    lazy val `vnd.bpf3`: MediaType = vndBpf3

    lazy val vndBusinessobjects: MediaType =
      MediaType("application", "vnd.businessobjects", compressible = false, binary = true, fileExtensions = List("rep"))
    
    lazy val `vnd.businessobjects`: MediaType = vndBusinessobjects

    lazy val vndByuUapiJson: MediaType =
      MediaType("application", "vnd.byu.uapi+json", compressible = true, binary = false)
    
    lazy val `vnd.byu.uapi+json`: MediaType = vndByuUapiJson

    lazy val vndBzip3: MediaType =
      MediaType("application", "vnd.bzip3", compressible = false, binary = true)
    
    lazy val `vnd.bzip3`: MediaType = vndBzip3

    lazy val vndC3vocScheduleXml: MediaType =
      MediaType("application", "vnd.c3voc.schedule+xml", compressible = true, binary = true)
    
    lazy val `vnd.c3voc.schedule+xml`: MediaType = vndC3vocScheduleXml

    lazy val vndCabJscript: MediaType =
      MediaType("application", "vnd.cab-jscript", compressible = false, binary = true)
    
    lazy val `vnd.cab-jscript`: MediaType = vndCabJscript

    lazy val vndCanonCpdl: MediaType =
      MediaType("application", "vnd.canon-cpdl", compressible = false, binary = true)
    
    lazy val `vnd.canon-cpdl`: MediaType = vndCanonCpdl

    lazy val vndCanonLips: MediaType =
      MediaType("application", "vnd.canon-lips", compressible = false, binary = true)
    
    lazy val `vnd.canon-lips`: MediaType = vndCanonLips

    lazy val vndCapasystemsPgJson: MediaType =
      MediaType("application", "vnd.capasystems-pg+json", compressible = true, binary = false)
    
    lazy val `vnd.capasystems-pg+json`: MediaType = vndCapasystemsPgJson

    lazy val vndCel: MediaType =
      MediaType("application", "vnd.cel", compressible = false, binary = true)
    
    lazy val `vnd.cel`: MediaType = vndCel

    lazy val vndCendioThinlincClientconf: MediaType =
      MediaType("application", "vnd.cendio.thinlinc.clientconf", compressible = false, binary = true)
    
    lazy val `vnd.cendio.thinlinc.clientconf`: MediaType = vndCendioThinlincClientconf

    lazy val vndCenturySystemsTcpStream: MediaType =
      MediaType("application", "vnd.century-systems.tcp_stream", compressible = false, binary = true)
    
    lazy val `vnd.century-systems.tcp_stream`: MediaType = vndCenturySystemsTcpStream

    lazy val vndChemdrawXml: MediaType =
      MediaType("application", "vnd.chemdraw+xml", compressible = true, binary = true, fileExtensions = List("cdxml"))
    
    lazy val `vnd.chemdraw+xml`: MediaType = vndChemdrawXml

    lazy val vndChessPgn: MediaType =
      MediaType("application", "vnd.chess-pgn", compressible = false, binary = true)
    
    lazy val `vnd.chess-pgn`: MediaType = vndChessPgn

    lazy val vndChipnutsKaraokeMmd: MediaType =
      MediaType("application", "vnd.chipnuts.karaoke-mmd", compressible = false, binary = true, fileExtensions = List("mmd"))
    
    lazy val `vnd.chipnuts.karaoke-mmd`: MediaType = vndChipnutsKaraokeMmd

    lazy val vndCiedi: MediaType =
      MediaType("application", "vnd.ciedi", compressible = false, binary = true)
    
    lazy val `vnd.ciedi`: MediaType = vndCiedi

    lazy val vndCinderella: MediaType =
      MediaType("application", "vnd.cinderella", compressible = false, binary = true, fileExtensions = List("cdy"))
    
    lazy val `vnd.cinderella`: MediaType = vndCinderella

    lazy val vndCirpackIsdnExt: MediaType =
      MediaType("application", "vnd.cirpack.isdn-ext", compressible = false, binary = true)
    
    lazy val `vnd.cirpack.isdn-ext`: MediaType = vndCirpackIsdnExt

    lazy val vndCitationstylesStyleXml: MediaType =
      MediaType("application", "vnd.citationstyles.style+xml", compressible = true, binary = true, fileExtensions = List("csl"))
    
    lazy val `vnd.citationstyles.style+xml`: MediaType = vndCitationstylesStyleXml

    lazy val vndClaymore: MediaType =
      MediaType("application", "vnd.claymore", compressible = false, binary = true, fileExtensions = List("cla"))
    
    lazy val `vnd.claymore`: MediaType = vndClaymore

    lazy val vndCloantoRp9: MediaType =
      MediaType("application", "vnd.cloanto.rp9", compressible = false, binary = true, fileExtensions = List("rp9"))
    
    lazy val `vnd.cloanto.rp9`: MediaType = vndCloantoRp9

    lazy val vndClonkC4group: MediaType =
      MediaType("application", "vnd.clonk.c4group", compressible = false, binary = true, fileExtensions = List("c4g", "c4d", "c4f", "c4p", "c4u"))
    
    lazy val `vnd.clonk.c4group`: MediaType = vndClonkC4group

    lazy val vndCluetrustCartomobileConfig: MediaType =
      MediaType("application", "vnd.cluetrust.cartomobile-config", compressible = false, binary = true, fileExtensions = List("c11amc"))
    
    lazy val `vnd.cluetrust.cartomobile-config`: MediaType = vndCluetrustCartomobileConfig

    lazy val vndCluetrustCartomobileConfigPkg: MediaType =
      MediaType("application", "vnd.cluetrust.cartomobile-config-pkg", compressible = false, binary = true, fileExtensions = List("c11amz"))
    
    lazy val `vnd.cluetrust.cartomobile-config-pkg`: MediaType = vndCluetrustCartomobileConfigPkg

    lazy val vndCncfHelmChartContentV1TarGzip: MediaType =
      MediaType("application", "vnd.cncf.helm.chart.content.v1.tar+gzip", compressible = false, binary = true)
    
    lazy val `vnd.cncf.helm.chart.content.v1.tar+gzip`: MediaType = vndCncfHelmChartContentV1TarGzip

    lazy val vndCncfHelmChartProvenanceV1Prov: MediaType =
      MediaType("application", "vnd.cncf.helm.chart.provenance.v1.prov", compressible = false, binary = true)
    
    lazy val `vnd.cncf.helm.chart.provenance.v1.prov`: MediaType = vndCncfHelmChartProvenanceV1Prov

    lazy val vndCncfHelmConfigV1Json: MediaType =
      MediaType("application", "vnd.cncf.helm.config.v1+json", compressible = true, binary = false)
    
    lazy val `vnd.cncf.helm.config.v1+json`: MediaType = vndCncfHelmConfigV1Json

    lazy val vndCoffeescript: MediaType =
      MediaType("application", "vnd.coffeescript", compressible = false, binary = true)
    
    lazy val `vnd.coffeescript`: MediaType = vndCoffeescript

    lazy val vndCollabioXodocumentsDocument: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.document", compressible = false, binary = true)
    
    lazy val `vnd.collabio.xodocuments.document`: MediaType = vndCollabioXodocumentsDocument

    lazy val vndCollabioXodocumentsDocumentTemplate: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.document-template", compressible = false, binary = true)
    
    lazy val `vnd.collabio.xodocuments.document-template`: MediaType = vndCollabioXodocumentsDocumentTemplate

    lazy val vndCollabioXodocumentsPresentation: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.presentation", compressible = false, binary = true)
    
    lazy val `vnd.collabio.xodocuments.presentation`: MediaType = vndCollabioXodocumentsPresentation

    lazy val vndCollabioXodocumentsPresentationTemplate: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.presentation-template", compressible = false, binary = true)
    
    lazy val `vnd.collabio.xodocuments.presentation-template`: MediaType = vndCollabioXodocumentsPresentationTemplate

    lazy val vndCollabioXodocumentsSpreadsheet: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.spreadsheet", compressible = false, binary = true)
    
    lazy val `vnd.collabio.xodocuments.spreadsheet`: MediaType = vndCollabioXodocumentsSpreadsheet

    lazy val vndCollabioXodocumentsSpreadsheetTemplate: MediaType =
      MediaType("application", "vnd.collabio.xodocuments.spreadsheet-template", compressible = false, binary = true)
    
    lazy val `vnd.collabio.xodocuments.spreadsheet-template`: MediaType = vndCollabioXodocumentsSpreadsheetTemplate

    lazy val vndCollectionJson: MediaType =
      MediaType("application", "vnd.collection+json", compressible = true, binary = false)
    
    lazy val `vnd.collection+json`: MediaType = vndCollectionJson

    lazy val vndCollectionDocJson: MediaType =
      MediaType("application", "vnd.collection.doc+json", compressible = true, binary = false)
    
    lazy val `vnd.collection.doc+json`: MediaType = vndCollectionDocJson

    lazy val vndCollectionNextJson: MediaType =
      MediaType("application", "vnd.collection.next+json", compressible = true, binary = false)
    
    lazy val `vnd.collection.next+json`: MediaType = vndCollectionNextJson

    lazy val vndComicbookZip: MediaType =
      MediaType("application", "vnd.comicbook+zip", compressible = false, binary = true)
    
    lazy val `vnd.comicbook+zip`: MediaType = vndComicbookZip

    lazy val vndComicbookRar: MediaType =
      MediaType("application", "vnd.comicbook-rar", compressible = false, binary = true)
    
    lazy val `vnd.comicbook-rar`: MediaType = vndComicbookRar

    lazy val vndCommerceBattelle: MediaType =
      MediaType("application", "vnd.commerce-battelle", compressible = false, binary = true)
    
    lazy val `vnd.commerce-battelle`: MediaType = vndCommerceBattelle

    lazy val vndCommonspace: MediaType =
      MediaType("application", "vnd.commonspace", compressible = false, binary = true, fileExtensions = List("csp"))
    
    lazy val `vnd.commonspace`: MediaType = vndCommonspace

    lazy val vndContactCmsg: MediaType =
      MediaType("application", "vnd.contact.cmsg", compressible = false, binary = true, fileExtensions = List("cdbcmsg"))
    
    lazy val `vnd.contact.cmsg`: MediaType = vndContactCmsg

    lazy val vndCoreosIgnitionJson: MediaType =
      MediaType("application", "vnd.coreos.ignition+json", compressible = true, binary = false)
    
    lazy val `vnd.coreos.ignition+json`: MediaType = vndCoreosIgnitionJson

    lazy val vndCosmocaller: MediaType =
      MediaType("application", "vnd.cosmocaller", compressible = false, binary = true, fileExtensions = List("cmc"))
    
    lazy val `vnd.cosmocaller`: MediaType = vndCosmocaller

    lazy val vndCrickClicker: MediaType =
      MediaType("application", "vnd.crick.clicker", compressible = false, binary = true, fileExtensions = List("clkx"))
    
    lazy val `vnd.crick.clicker`: MediaType = vndCrickClicker

    lazy val vndCrickClickerKeyboard: MediaType =
      MediaType("application", "vnd.crick.clicker.keyboard", compressible = false, binary = true, fileExtensions = List("clkk"))
    
    lazy val `vnd.crick.clicker.keyboard`: MediaType = vndCrickClickerKeyboard

    lazy val vndCrickClickerPalette: MediaType =
      MediaType("application", "vnd.crick.clicker.palette", compressible = false, binary = true, fileExtensions = List("clkp"))
    
    lazy val `vnd.crick.clicker.palette`: MediaType = vndCrickClickerPalette

    lazy val vndCrickClickerTemplate: MediaType =
      MediaType("application", "vnd.crick.clicker.template", compressible = false, binary = true, fileExtensions = List("clkt"))
    
    lazy val `vnd.crick.clicker.template`: MediaType = vndCrickClickerTemplate

    lazy val vndCrickClickerWordbank: MediaType =
      MediaType("application", "vnd.crick.clicker.wordbank", compressible = false, binary = true, fileExtensions = List("clkw"))
    
    lazy val `vnd.crick.clicker.wordbank`: MediaType = vndCrickClickerWordbank

    lazy val vndCriticaltoolsWbsXml: MediaType =
      MediaType("application", "vnd.criticaltools.wbs+xml", compressible = true, binary = true, fileExtensions = List("wbs"))
    
    lazy val `vnd.criticaltools.wbs+xml`: MediaType = vndCriticaltoolsWbsXml

    lazy val vndCryptiiPipeJson: MediaType =
      MediaType("application", "vnd.cryptii.pipe+json", compressible = true, binary = false)
    
    lazy val `vnd.cryptii.pipe+json`: MediaType = vndCryptiiPipeJson

    lazy val vndCryptoShadeFile: MediaType =
      MediaType("application", "vnd.crypto-shade-file", compressible = false, binary = true)
    
    lazy val `vnd.crypto-shade-file`: MediaType = vndCryptoShadeFile

    lazy val vndCryptomatorEncrypted: MediaType =
      MediaType("application", "vnd.cryptomator.encrypted", compressible = false, binary = true)
    
    lazy val `vnd.cryptomator.encrypted`: MediaType = vndCryptomatorEncrypted

    lazy val vndCryptomatorVault: MediaType =
      MediaType("application", "vnd.cryptomator.vault", compressible = false, binary = true)
    
    lazy val `vnd.cryptomator.vault`: MediaType = vndCryptomatorVault

    lazy val vndCtcPosml: MediaType =
      MediaType("application", "vnd.ctc-posml", compressible = false, binary = true, fileExtensions = List("pml"))
    
    lazy val `vnd.ctc-posml`: MediaType = vndCtcPosml

    lazy val vndCtctWsXml: MediaType =
      MediaType("application", "vnd.ctct.ws+xml", compressible = true, binary = true)
    
    lazy val `vnd.ctct.ws+xml`: MediaType = vndCtctWsXml

    lazy val vndCupsPdf: MediaType =
      MediaType("application", "vnd.cups-pdf", compressible = false, binary = true)
    
    lazy val `vnd.cups-pdf`: MediaType = vndCupsPdf

    lazy val vndCupsPostscript: MediaType =
      MediaType("application", "vnd.cups-postscript", compressible = false, binary = true)
    
    lazy val `vnd.cups-postscript`: MediaType = vndCupsPostscript

    lazy val vndCupsPpd: MediaType =
      MediaType("application", "vnd.cups-ppd", compressible = false, binary = true, fileExtensions = List("ppd"))
    
    lazy val `vnd.cups-ppd`: MediaType = vndCupsPpd

    lazy val vndCupsRaster: MediaType =
      MediaType("application", "vnd.cups-raster", compressible = false, binary = true)
    
    lazy val `vnd.cups-raster`: MediaType = vndCupsRaster

    lazy val vndCupsRaw: MediaType =
      MediaType("application", "vnd.cups-raw", compressible = false, binary = true)
    
    lazy val `vnd.cups-raw`: MediaType = vndCupsRaw

    lazy val vndCurl: MediaType =
      MediaType("application", "vnd.curl", compressible = false, binary = true)
    
    lazy val `vnd.curl`: MediaType = vndCurl

    lazy val vndCurlCar: MediaType =
      MediaType("application", "vnd.curl.car", compressible = false, binary = true, fileExtensions = List("car"))
    
    lazy val `vnd.curl.car`: MediaType = vndCurlCar

    lazy val vndCurlPcurl: MediaType =
      MediaType("application", "vnd.curl.pcurl", compressible = false, binary = true, fileExtensions = List("pcurl"))
    
    lazy val `vnd.curl.pcurl`: MediaType = vndCurlPcurl

    lazy val vndCyanDeanRootXml: MediaType =
      MediaType("application", "vnd.cyan.dean.root+xml", compressible = true, binary = true)
    
    lazy val `vnd.cyan.dean.root+xml`: MediaType = vndCyanDeanRootXml

    lazy val vndCybank: MediaType =
      MediaType("application", "vnd.cybank", compressible = false, binary = true)
    
    lazy val `vnd.cybank`: MediaType = vndCybank

    lazy val vndCyclonedxJson: MediaType =
      MediaType("application", "vnd.cyclonedx+json", compressible = true, binary = false)
    
    lazy val `vnd.cyclonedx+json`: MediaType = vndCyclonedxJson

    lazy val vndCyclonedxXml: MediaType =
      MediaType("application", "vnd.cyclonedx+xml", compressible = true, binary = true)
    
    lazy val `vnd.cyclonedx+xml`: MediaType = vndCyclonedxXml

    lazy val vndD2lCoursepackage1p0Zip: MediaType =
      MediaType("application", "vnd.d2l.coursepackage1p0+zip", compressible = false, binary = true)
    
    lazy val `vnd.d2l.coursepackage1p0+zip`: MediaType = vndD2lCoursepackage1p0Zip

    lazy val vndD3mDataset: MediaType =
      MediaType("application", "vnd.d3m-dataset", compressible = false, binary = true)
    
    lazy val `vnd.d3m-dataset`: MediaType = vndD3mDataset

    lazy val vndD3mProblem: MediaType =
      MediaType("application", "vnd.d3m-problem", compressible = false, binary = true)
    
    lazy val `vnd.d3m-problem`: MediaType = vndD3mProblem

    lazy val vndDart: MediaType =
      MediaType("application", "vnd.dart", compressible = true, binary = true, fileExtensions = List("dart"))
    
    lazy val `vnd.dart`: MediaType = vndDart

    lazy val vndDataVisionRdz: MediaType =
      MediaType("application", "vnd.data-vision.rdz", compressible = false, binary = true, fileExtensions = List("rdz"))
    
    lazy val `vnd.data-vision.rdz`: MediaType = vndDataVisionRdz

    lazy val vndDatalog: MediaType =
      MediaType("application", "vnd.datalog", compressible = false, binary = true)
    
    lazy val `vnd.datalog`: MediaType = vndDatalog

    lazy val vndDatapackageJson: MediaType =
      MediaType("application", "vnd.datapackage+json", compressible = true, binary = false)
    
    lazy val `vnd.datapackage+json`: MediaType = vndDatapackageJson

    lazy val vndDataresourceJson: MediaType =
      MediaType("application", "vnd.dataresource+json", compressible = true, binary = false)
    
    lazy val `vnd.dataresource+json`: MediaType = vndDataresourceJson

    lazy val vndDbf: MediaType =
      MediaType("application", "vnd.dbf", compressible = false, binary = true, fileExtensions = List("dbf"))
    
    lazy val `vnd.dbf`: MediaType = vndDbf

    lazy val vndDcmpXml: MediaType =
      MediaType("application", "vnd.dcmp+xml", compressible = true, binary = true, fileExtensions = List("dcmp"))
    
    lazy val `vnd.dcmp+xml`: MediaType = vndDcmpXml

    lazy val vndDebianBinaryPackage: MediaType =
      MediaType("application", "vnd.debian.binary-package", compressible = false, binary = true)
    
    lazy val `vnd.debian.binary-package`: MediaType = vndDebianBinaryPackage

    lazy val vndDeceData: MediaType =
      MediaType("application", "vnd.dece.data", compressible = false, binary = true, fileExtensions = List("uvf", "uvvf", "uvd", "uvvd"))
    
    lazy val `vnd.dece.data`: MediaType = vndDeceData

    lazy val vndDeceTtmlXml: MediaType =
      MediaType("application", "vnd.dece.ttml+xml", compressible = true, binary = true, fileExtensions = List("uvt", "uvvt"))
    
    lazy val `vnd.dece.ttml+xml`: MediaType = vndDeceTtmlXml

    lazy val vndDeceUnspecified: MediaType =
      MediaType("application", "vnd.dece.unspecified", compressible = false, binary = true, fileExtensions = List("uvx", "uvvx"))
    
    lazy val `vnd.dece.unspecified`: MediaType = vndDeceUnspecified

    lazy val vndDeceZip: MediaType =
      MediaType("application", "vnd.dece.zip", compressible = false, binary = true, fileExtensions = List("uvz", "uvvz"))
    
    lazy val `vnd.dece.zip`: MediaType = vndDeceZip

    lazy val vndDenovoFcselayoutLink: MediaType =
      MediaType("application", "vnd.denovo.fcselayout-link", compressible = false, binary = true, fileExtensions = List("fe_launch"))
    
    lazy val `vnd.denovo.fcselayout-link`: MediaType = vndDenovoFcselayoutLink

    lazy val vndDesmumeMovie: MediaType =
      MediaType("application", "vnd.desmume.movie", compressible = false, binary = true)
    
    lazy val `vnd.desmume.movie`: MediaType = vndDesmumeMovie

    lazy val vndDirBiPlateDlNosuffix: MediaType =
      MediaType("application", "vnd.dir-bi.plate-dl-nosuffix", compressible = false, binary = true)
    
    lazy val `vnd.dir-bi.plate-dl-nosuffix`: MediaType = vndDirBiPlateDlNosuffix

    lazy val vndDmDelegationXml: MediaType =
      MediaType("application", "vnd.dm.delegation+xml", compressible = true, binary = true)
    
    lazy val `vnd.dm.delegation+xml`: MediaType = vndDmDelegationXml

    lazy val vndDna: MediaType =
      MediaType("application", "vnd.dna", compressible = false, binary = true, fileExtensions = List("dna"))
    
    lazy val `vnd.dna`: MediaType = vndDna

    lazy val vndDocumentJson: MediaType =
      MediaType("application", "vnd.document+json", compressible = true, binary = false)
    
    lazy val `vnd.document+json`: MediaType = vndDocumentJson

    lazy val vndDolbyMlp: MediaType =
      MediaType("application", "vnd.dolby.mlp", compressible = false, binary = true, fileExtensions = List("mlp"))
    
    lazy val `vnd.dolby.mlp`: MediaType = vndDolbyMlp

    lazy val vndDolbyMobile1: MediaType =
      MediaType("application", "vnd.dolby.mobile.1", compressible = false, binary = true)
    
    lazy val `vnd.dolby.mobile.1`: MediaType = vndDolbyMobile1

    lazy val vndDolbyMobile2: MediaType =
      MediaType("application", "vnd.dolby.mobile.2", compressible = false, binary = true)
    
    lazy val `vnd.dolby.mobile.2`: MediaType = vndDolbyMobile2

    lazy val vndDoremirScorecloudBinaryDocument: MediaType =
      MediaType("application", "vnd.doremir.scorecloud-binary-document", compressible = false, binary = true)
    
    lazy val `vnd.doremir.scorecloud-binary-document`: MediaType = vndDoremirScorecloudBinaryDocument

    lazy val vndDpgraph: MediaType =
      MediaType("application", "vnd.dpgraph", compressible = false, binary = true, fileExtensions = List("dpg"))
    
    lazy val `vnd.dpgraph`: MediaType = vndDpgraph

    lazy val vndDreamfactory: MediaType =
      MediaType("application", "vnd.dreamfactory", compressible = false, binary = true, fileExtensions = List("dfac"))
    
    lazy val `vnd.dreamfactory`: MediaType = vndDreamfactory

    lazy val vndDriveJson: MediaType =
      MediaType("application", "vnd.drive+json", compressible = true, binary = false)
    
    lazy val `vnd.drive+json`: MediaType = vndDriveJson

    lazy val vndDsKeypoint: MediaType =
      MediaType("application", "vnd.ds-keypoint", compressible = false, binary = true, fileExtensions = List("kpxx"))
    
    lazy val `vnd.ds-keypoint`: MediaType = vndDsKeypoint

    lazy val vndDtgLocal: MediaType =
      MediaType("application", "vnd.dtg.local", compressible = false, binary = true)
    
    lazy val `vnd.dtg.local`: MediaType = vndDtgLocal

    lazy val vndDtgLocalFlash: MediaType =
      MediaType("application", "vnd.dtg.local.flash", compressible = false, binary = true)
    
    lazy val `vnd.dtg.local.flash`: MediaType = vndDtgLocalFlash

    lazy val vndDtgLocalHtml: MediaType =
      MediaType("application", "vnd.dtg.local.html", compressible = false, binary = true)
    
    lazy val `vnd.dtg.local.html`: MediaType = vndDtgLocalHtml

    lazy val vndDvbAit: MediaType =
      MediaType("application", "vnd.dvb.ait", compressible = false, binary = true, fileExtensions = List("ait"))
    
    lazy val `vnd.dvb.ait`: MediaType = vndDvbAit

    lazy val vndDvbDvbislXml: MediaType =
      MediaType("application", "vnd.dvb.dvbisl+xml", compressible = true, binary = true)
    
    lazy val `vnd.dvb.dvbisl+xml`: MediaType = vndDvbDvbislXml

    lazy val vndDvbDvbj: MediaType =
      MediaType("application", "vnd.dvb.dvbj", compressible = false, binary = true)
    
    lazy val `vnd.dvb.dvbj`: MediaType = vndDvbDvbj

    lazy val vndDvbEsgcontainer: MediaType =
      MediaType("application", "vnd.dvb.esgcontainer", compressible = false, binary = true)
    
    lazy val `vnd.dvb.esgcontainer`: MediaType = vndDvbEsgcontainer

    lazy val vndDvbIpdcdftnotifaccess: MediaType =
      MediaType("application", "vnd.dvb.ipdcdftnotifaccess", compressible = false, binary = true)
    
    lazy val `vnd.dvb.ipdcdftnotifaccess`: MediaType = vndDvbIpdcdftnotifaccess

    lazy val vndDvbIpdcesgaccess: MediaType =
      MediaType("application", "vnd.dvb.ipdcesgaccess", compressible = false, binary = true)
    
    lazy val `vnd.dvb.ipdcesgaccess`: MediaType = vndDvbIpdcesgaccess

    lazy val vndDvbIpdcesgaccess2: MediaType =
      MediaType("application", "vnd.dvb.ipdcesgaccess2", compressible = false, binary = true)
    
    lazy val `vnd.dvb.ipdcesgaccess2`: MediaType = vndDvbIpdcesgaccess2

    lazy val vndDvbIpdcesgpdd: MediaType =
      MediaType("application", "vnd.dvb.ipdcesgpdd", compressible = false, binary = true)
    
    lazy val `vnd.dvb.ipdcesgpdd`: MediaType = vndDvbIpdcesgpdd

    lazy val vndDvbIpdcroaming: MediaType =
      MediaType("application", "vnd.dvb.ipdcroaming", compressible = false, binary = true)
    
    lazy val `vnd.dvb.ipdcroaming`: MediaType = vndDvbIpdcroaming

    lazy val vndDvbIptvAlfecBase: MediaType =
      MediaType("application", "vnd.dvb.iptv.alfec-base", compressible = false, binary = true)
    
    lazy val `vnd.dvb.iptv.alfec-base`: MediaType = vndDvbIptvAlfecBase

    lazy val vndDvbIptvAlfecEnhancement: MediaType =
      MediaType("application", "vnd.dvb.iptv.alfec-enhancement", compressible = false, binary = true)
    
    lazy val `vnd.dvb.iptv.alfec-enhancement`: MediaType = vndDvbIptvAlfecEnhancement

    lazy val vndDvbNotifAggregateRootXml: MediaType =
      MediaType("application", "vnd.dvb.notif-aggregate-root+xml", compressible = true, binary = true)
    
    lazy val `vnd.dvb.notif-aggregate-root+xml`: MediaType = vndDvbNotifAggregateRootXml

    lazy val vndDvbNotifContainerXml: MediaType =
      MediaType("application", "vnd.dvb.notif-container+xml", compressible = true, binary = true)
    
    lazy val `vnd.dvb.notif-container+xml`: MediaType = vndDvbNotifContainerXml

    lazy val vndDvbNotifGenericXml: MediaType =
      MediaType("application", "vnd.dvb.notif-generic+xml", compressible = true, binary = true)
    
    lazy val `vnd.dvb.notif-generic+xml`: MediaType = vndDvbNotifGenericXml

    lazy val vndDvbNotifIaMsglistXml: MediaType =
      MediaType("application", "vnd.dvb.notif-ia-msglist+xml", compressible = true, binary = true)
    
    lazy val `vnd.dvb.notif-ia-msglist+xml`: MediaType = vndDvbNotifIaMsglistXml

    lazy val vndDvbNotifIaRegistrationRequestXml: MediaType =
      MediaType("application", "vnd.dvb.notif-ia-registration-request+xml", compressible = true, binary = true)
    
    lazy val `vnd.dvb.notif-ia-registration-request+xml`: MediaType = vndDvbNotifIaRegistrationRequestXml

    lazy val vndDvbNotifIaRegistrationResponseXml: MediaType =
      MediaType("application", "vnd.dvb.notif-ia-registration-response+xml", compressible = true, binary = true)
    
    lazy val `vnd.dvb.notif-ia-registration-response+xml`: MediaType = vndDvbNotifIaRegistrationResponseXml

    lazy val vndDvbNotifInitXml: MediaType =
      MediaType("application", "vnd.dvb.notif-init+xml", compressible = true, binary = true)
    
    lazy val `vnd.dvb.notif-init+xml`: MediaType = vndDvbNotifInitXml

    lazy val vndDvbPfr: MediaType =
      MediaType("application", "vnd.dvb.pfr", compressible = false, binary = true)
    
    lazy val `vnd.dvb.pfr`: MediaType = vndDvbPfr

    lazy val vndDvbService: MediaType =
      MediaType("application", "vnd.dvb.service", compressible = false, binary = true, fileExtensions = List("svc"))
    
    lazy val `vnd.dvb.service`: MediaType = vndDvbService

    lazy val vndDxr: MediaType =
      MediaType("application", "vnd.dxr", compressible = false, binary = true)
    
    lazy val `vnd.dxr`: MediaType = vndDxr

    lazy val vndDynageo: MediaType =
      MediaType("application", "vnd.dynageo", compressible = false, binary = true, fileExtensions = List("geo"))
    
    lazy val `vnd.dynageo`: MediaType = vndDynageo

    lazy val vndDzr: MediaType =
      MediaType("application", "vnd.dzr", compressible = false, binary = true)
    
    lazy val `vnd.dzr`: MediaType = vndDzr

    lazy val vndEasykaraokeCdgdownload: MediaType =
      MediaType("application", "vnd.easykaraoke.cdgdownload", compressible = false, binary = true)
    
    lazy val `vnd.easykaraoke.cdgdownload`: MediaType = vndEasykaraokeCdgdownload

    lazy val vndEcdisUpdate: MediaType =
      MediaType("application", "vnd.ecdis-update", compressible = false, binary = true)
    
    lazy val `vnd.ecdis-update`: MediaType = vndEcdisUpdate

    lazy val vndEcipRlp: MediaType =
      MediaType("application", "vnd.ecip.rlp", compressible = false, binary = true)
    
    lazy val `vnd.ecip.rlp`: MediaType = vndEcipRlp

    lazy val vndEclipseDittoJson: MediaType =
      MediaType("application", "vnd.eclipse.ditto+json", compressible = true, binary = false)
    
    lazy val `vnd.eclipse.ditto+json`: MediaType = vndEclipseDittoJson

    lazy val vndEcowinChart: MediaType =
      MediaType("application", "vnd.ecowin.chart", compressible = false, binary = true, fileExtensions = List("mag"))
    
    lazy val `vnd.ecowin.chart`: MediaType = vndEcowinChart

    lazy val vndEcowinFilerequest: MediaType =
      MediaType("application", "vnd.ecowin.filerequest", compressible = false, binary = true)
    
    lazy val `vnd.ecowin.filerequest`: MediaType = vndEcowinFilerequest

    lazy val vndEcowinFileupdate: MediaType =
      MediaType("application", "vnd.ecowin.fileupdate", compressible = false, binary = true)
    
    lazy val `vnd.ecowin.fileupdate`: MediaType = vndEcowinFileupdate

    lazy val vndEcowinSeries: MediaType =
      MediaType("application", "vnd.ecowin.series", compressible = false, binary = true)
    
    lazy val `vnd.ecowin.series`: MediaType = vndEcowinSeries

    lazy val vndEcowinSeriesrequest: MediaType =
      MediaType("application", "vnd.ecowin.seriesrequest", compressible = false, binary = true)
    
    lazy val `vnd.ecowin.seriesrequest`: MediaType = vndEcowinSeriesrequest

    lazy val vndEcowinSeriesupdate: MediaType =
      MediaType("application", "vnd.ecowin.seriesupdate", compressible = false, binary = true)
    
    lazy val `vnd.ecowin.seriesupdate`: MediaType = vndEcowinSeriesupdate

    lazy val vndEfiImg: MediaType =
      MediaType("application", "vnd.efi.img", compressible = false, binary = true)
    
    lazy val `vnd.efi.img`: MediaType = vndEfiImg

    lazy val vndEfiIso: MediaType =
      MediaType("application", "vnd.efi.iso", compressible = false, binary = true)
    
    lazy val `vnd.efi.iso`: MediaType = vndEfiIso

    lazy val vndElnZip: MediaType =
      MediaType("application", "vnd.eln+zip", compressible = false, binary = true)
    
    lazy val `vnd.eln+zip`: MediaType = vndElnZip

    lazy val vndEmclientAccessrequestXml: MediaType =
      MediaType("application", "vnd.emclient.accessrequest+xml", compressible = true, binary = true)
    
    lazy val `vnd.emclient.accessrequest+xml`: MediaType = vndEmclientAccessrequestXml

    lazy val vndEnliven: MediaType =
      MediaType("application", "vnd.enliven", compressible = false, binary = true, fileExtensions = List("nml"))
    
    lazy val `vnd.enliven`: MediaType = vndEnliven

    lazy val vndEnphaseEnvoy: MediaType =
      MediaType("application", "vnd.enphase.envoy", compressible = false, binary = true)
    
    lazy val `vnd.enphase.envoy`: MediaType = vndEnphaseEnvoy

    lazy val vndEprintsDataXml: MediaType =
      MediaType("application", "vnd.eprints.data+xml", compressible = true, binary = true)
    
    lazy val `vnd.eprints.data+xml`: MediaType = vndEprintsDataXml

    lazy val vndEpsonEsf: MediaType =
      MediaType("application", "vnd.epson.esf", compressible = false, binary = true, fileExtensions = List("esf"))
    
    lazy val `vnd.epson.esf`: MediaType = vndEpsonEsf

    lazy val vndEpsonMsf: MediaType =
      MediaType("application", "vnd.epson.msf", compressible = false, binary = true, fileExtensions = List("msf"))
    
    lazy val `vnd.epson.msf`: MediaType = vndEpsonMsf

    lazy val vndEpsonQuickanime: MediaType =
      MediaType("application", "vnd.epson.quickanime", compressible = false, binary = true, fileExtensions = List("qam"))
    
    lazy val `vnd.epson.quickanime`: MediaType = vndEpsonQuickanime

    lazy val vndEpsonSalt: MediaType =
      MediaType("application", "vnd.epson.salt", compressible = false, binary = true, fileExtensions = List("slt"))
    
    lazy val `vnd.epson.salt`: MediaType = vndEpsonSalt

    lazy val vndEpsonSsf: MediaType =
      MediaType("application", "vnd.epson.ssf", compressible = false, binary = true, fileExtensions = List("ssf"))
    
    lazy val `vnd.epson.ssf`: MediaType = vndEpsonSsf

    lazy val vndEricssonQuickcall: MediaType =
      MediaType("application", "vnd.ericsson.quickcall", compressible = false, binary = true)
    
    lazy val `vnd.ericsson.quickcall`: MediaType = vndEricssonQuickcall

    lazy val vndErofs: MediaType =
      MediaType("application", "vnd.erofs", compressible = false, binary = true)
    
    lazy val `vnd.erofs`: MediaType = vndErofs

    lazy val vndEspassEspassZip: MediaType =
      MediaType("application", "vnd.espass-espass+zip", compressible = false, binary = true)
    
    lazy val `vnd.espass-espass+zip`: MediaType = vndEspassEspassZip

    lazy val vndEszigno3Xml: MediaType =
      MediaType("application", "vnd.eszigno3+xml", compressible = true, binary = true, fileExtensions = List("es3", "et3"))
    
    lazy val `vnd.eszigno3+xml`: MediaType = vndEszigno3Xml

    lazy val vndEtsiAocXml: MediaType =
      MediaType("application", "vnd.etsi.aoc+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.aoc+xml`: MediaType = vndEtsiAocXml

    lazy val vndEtsiAsicEZip: MediaType =
      MediaType("application", "vnd.etsi.asic-e+zip", compressible = false, binary = true)
    
    lazy val `vnd.etsi.asic-e+zip`: MediaType = vndEtsiAsicEZip

    lazy val vndEtsiAsicSZip: MediaType =
      MediaType("application", "vnd.etsi.asic-s+zip", compressible = false, binary = true)
    
    lazy val `vnd.etsi.asic-s+zip`: MediaType = vndEtsiAsicSZip

    lazy val vndEtsiCugXml: MediaType =
      MediaType("application", "vnd.etsi.cug+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.cug+xml`: MediaType = vndEtsiCugXml

    lazy val vndEtsiIptvcommandXml: MediaType =
      MediaType("application", "vnd.etsi.iptvcommand+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.iptvcommand+xml`: MediaType = vndEtsiIptvcommandXml

    lazy val vndEtsiIptvdiscoveryXml: MediaType =
      MediaType("application", "vnd.etsi.iptvdiscovery+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.iptvdiscovery+xml`: MediaType = vndEtsiIptvdiscoveryXml

    lazy val vndEtsiIptvprofileXml: MediaType =
      MediaType("application", "vnd.etsi.iptvprofile+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.iptvprofile+xml`: MediaType = vndEtsiIptvprofileXml

    lazy val vndEtsiIptvsadBcXml: MediaType =
      MediaType("application", "vnd.etsi.iptvsad-bc+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.iptvsad-bc+xml`: MediaType = vndEtsiIptvsadBcXml

    lazy val vndEtsiIptvsadCodXml: MediaType =
      MediaType("application", "vnd.etsi.iptvsad-cod+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.iptvsad-cod+xml`: MediaType = vndEtsiIptvsadCodXml

    lazy val vndEtsiIptvsadNpvrXml: MediaType =
      MediaType("application", "vnd.etsi.iptvsad-npvr+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.iptvsad-npvr+xml`: MediaType = vndEtsiIptvsadNpvrXml

    lazy val vndEtsiIptvserviceXml: MediaType =
      MediaType("application", "vnd.etsi.iptvservice+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.iptvservice+xml`: MediaType = vndEtsiIptvserviceXml

    lazy val vndEtsiIptvsyncXml: MediaType =
      MediaType("application", "vnd.etsi.iptvsync+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.iptvsync+xml`: MediaType = vndEtsiIptvsyncXml

    lazy val vndEtsiIptvueprofileXml: MediaType =
      MediaType("application", "vnd.etsi.iptvueprofile+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.iptvueprofile+xml`: MediaType = vndEtsiIptvueprofileXml

    lazy val vndEtsiMcidXml: MediaType =
      MediaType("application", "vnd.etsi.mcid+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.mcid+xml`: MediaType = vndEtsiMcidXml

    lazy val vndEtsiMheg5: MediaType =
      MediaType("application", "vnd.etsi.mheg5", compressible = false, binary = true)
    
    lazy val `vnd.etsi.mheg5`: MediaType = vndEtsiMheg5

    lazy val vndEtsiOverloadControlPolicyDatasetXml: MediaType =
      MediaType("application", "vnd.etsi.overload-control-policy-dataset+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.overload-control-policy-dataset+xml`: MediaType = vndEtsiOverloadControlPolicyDatasetXml

    lazy val vndEtsiPstnXml: MediaType =
      MediaType("application", "vnd.etsi.pstn+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.pstn+xml`: MediaType = vndEtsiPstnXml

    lazy val vndEtsiSciXml: MediaType =
      MediaType("application", "vnd.etsi.sci+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.sci+xml`: MediaType = vndEtsiSciXml

    lazy val vndEtsiSimservsXml: MediaType =
      MediaType("application", "vnd.etsi.simservs+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.simservs+xml`: MediaType = vndEtsiSimservsXml

    lazy val vndEtsiTimestampToken: MediaType =
      MediaType("application", "vnd.etsi.timestamp-token", compressible = false, binary = true)
    
    lazy val `vnd.etsi.timestamp-token`: MediaType = vndEtsiTimestampToken

    lazy val vndEtsiTslXml: MediaType =
      MediaType("application", "vnd.etsi.tsl+xml", compressible = true, binary = true)
    
    lazy val `vnd.etsi.tsl+xml`: MediaType = vndEtsiTslXml

    lazy val vndEtsiTslDer: MediaType =
      MediaType("application", "vnd.etsi.tsl.der", compressible = false, binary = true)
    
    lazy val `vnd.etsi.tsl.der`: MediaType = vndEtsiTslDer

    lazy val vndEuKasparianCarJson: MediaType =
      MediaType("application", "vnd.eu.kasparian.car+json", compressible = true, binary = false)
    
    lazy val `vnd.eu.kasparian.car+json`: MediaType = vndEuKasparianCarJson

    lazy val vndEudoraData: MediaType =
      MediaType("application", "vnd.eudora.data", compressible = false, binary = true)
    
    lazy val `vnd.eudora.data`: MediaType = vndEudoraData

    lazy val vndEvolvEcigProfile: MediaType =
      MediaType("application", "vnd.evolv.ecig.profile", compressible = false, binary = true)
    
    lazy val `vnd.evolv.ecig.profile`: MediaType = vndEvolvEcigProfile

    lazy val vndEvolvEcigSettings: MediaType =
      MediaType("application", "vnd.evolv.ecig.settings", compressible = false, binary = true)
    
    lazy val `vnd.evolv.ecig.settings`: MediaType = vndEvolvEcigSettings

    lazy val vndEvolvEcigTheme: MediaType =
      MediaType("application", "vnd.evolv.ecig.theme", compressible = false, binary = true)
    
    lazy val `vnd.evolv.ecig.theme`: MediaType = vndEvolvEcigTheme

    lazy val vndExstreamEmpowerZip: MediaType =
      MediaType("application", "vnd.exstream-empower+zip", compressible = false, binary = true)
    
    lazy val `vnd.exstream-empower+zip`: MediaType = vndExstreamEmpowerZip

    lazy val vndExstreamPackage: MediaType =
      MediaType("application", "vnd.exstream-package", compressible = false, binary = true)
    
    lazy val `vnd.exstream-package`: MediaType = vndExstreamPackage

    lazy val vndEzpixAlbum: MediaType =
      MediaType("application", "vnd.ezpix-album", compressible = false, binary = true, fileExtensions = List("ez2"))
    
    lazy val `vnd.ezpix-album`: MediaType = vndEzpixAlbum

    lazy val vndEzpixPackage: MediaType =
      MediaType("application", "vnd.ezpix-package", compressible = false, binary = true, fileExtensions = List("ez3"))
    
    lazy val `vnd.ezpix-package`: MediaType = vndEzpixPackage

    lazy val vndFSecureMobile: MediaType =
      MediaType("application", "vnd.f-secure.mobile", compressible = false, binary = true)
    
    lazy val `vnd.f-secure.mobile`: MediaType = vndFSecureMobile

    lazy val vndFafYaml: MediaType =
      MediaType("application", "vnd.faf+yaml", compressible = false, binary = true)
    
    lazy val `vnd.faf+yaml`: MediaType = vndFafYaml

    lazy val vndFamilysearchGedcomZip: MediaType =
      MediaType("application", "vnd.familysearch.gedcom+zip", compressible = false, binary = true)
    
    lazy val `vnd.familysearch.gedcom+zip`: MediaType = vndFamilysearchGedcomZip

    lazy val vndFastcopyDiskImage: MediaType =
      MediaType("application", "vnd.fastcopy-disk-image", compressible = false, binary = true)
    
    lazy val `vnd.fastcopy-disk-image`: MediaType = vndFastcopyDiskImage

    lazy val vndFdf: MediaType =
      MediaType("application", "vnd.fdf", compressible = false, binary = true, fileExtensions = List("fdf"))
    
    lazy val `vnd.fdf`: MediaType = vndFdf

    lazy val vndFdsnMseed: MediaType =
      MediaType("application", "vnd.fdsn.mseed", compressible = false, binary = true, fileExtensions = List("mseed"))
    
    lazy val `vnd.fdsn.mseed`: MediaType = vndFdsnMseed

    lazy val vndFdsnSeed: MediaType =
      MediaType("application", "vnd.fdsn.seed", compressible = false, binary = true, fileExtensions = List("seed", "dataless"))
    
    lazy val `vnd.fdsn.seed`: MediaType = vndFdsnSeed

    lazy val vndFdsnStationxmlXml: MediaType =
      MediaType("application", "vnd.fdsn.stationxml+xml", compressible = true, binary = true)
    
    lazy val `vnd.fdsn.stationxml+xml`: MediaType = vndFdsnStationxmlXml

    lazy val vndFfsns: MediaType =
      MediaType("application", "vnd.ffsns", compressible = false, binary = true)
    
    lazy val `vnd.ffsns`: MediaType = vndFfsns

    lazy val vndFgb: MediaType =
      MediaType("application", "vnd.fgb", compressible = false, binary = true)
    
    lazy val `vnd.fgb`: MediaType = vndFgb

    lazy val vndFiclabFlbZip: MediaType =
      MediaType("application", "vnd.ficlab.flb+zip", compressible = false, binary = true)
    
    lazy val `vnd.ficlab.flb+zip`: MediaType = vndFiclabFlbZip

    lazy val vndFilmitZfc: MediaType =
      MediaType("application", "vnd.filmit.zfc", compressible = false, binary = true)
    
    lazy val `vnd.filmit.zfc`: MediaType = vndFilmitZfc

    lazy val vndFints: MediaType =
      MediaType("application", "vnd.fints", compressible = false, binary = true)
    
    lazy val `vnd.fints`: MediaType = vndFints

    lazy val vndFiremonkeysCloudcell: MediaType =
      MediaType("application", "vnd.firemonkeys.cloudcell", compressible = false, binary = true)
    
    lazy val `vnd.firemonkeys.cloudcell`: MediaType = vndFiremonkeysCloudcell

    lazy val vndFlographit: MediaType =
      MediaType("application", "vnd.flographit", compressible = false, binary = true, fileExtensions = List("gph"))
    
    lazy val `vnd.flographit`: MediaType = vndFlographit

    lazy val vndFluxtimeClip: MediaType =
      MediaType("application", "vnd.fluxtime.clip", compressible = false, binary = true, fileExtensions = List("ftc"))
    
    lazy val `vnd.fluxtime.clip`: MediaType = vndFluxtimeClip

    lazy val vndFontFontforgeSfd: MediaType =
      MediaType("application", "vnd.font-fontforge-sfd", compressible = false, binary = true)
    
    lazy val `vnd.font-fontforge-sfd`: MediaType = vndFontFontforgeSfd

    lazy val vndFramemaker: MediaType =
      MediaType("application", "vnd.framemaker", compressible = false, binary = true, fileExtensions = List("fm", "frame", "maker", "book"))
    
    lazy val `vnd.framemaker`: MediaType = vndFramemaker

    lazy val vndFreelogComic: MediaType =
      MediaType("application", "vnd.freelog.comic", compressible = false, binary = true)
    
    lazy val `vnd.freelog.comic`: MediaType = vndFreelogComic

    lazy val vndFrogansFnc: MediaType =
      MediaType("application", "vnd.frogans.fnc", compressible = false, binary = true, fileExtensions = List("fnc"))
    
    lazy val `vnd.frogans.fnc`: MediaType = vndFrogansFnc

    lazy val vndFrogansLtf: MediaType =
      MediaType("application", "vnd.frogans.ltf", compressible = false, binary = true, fileExtensions = List("ltf"))
    
    lazy val `vnd.frogans.ltf`: MediaType = vndFrogansLtf

    lazy val vndFscWeblaunch: MediaType =
      MediaType("application", "vnd.fsc.weblaunch", compressible = false, binary = true, fileExtensions = List("fsc"))
    
    lazy val `vnd.fsc.weblaunch`: MediaType = vndFscWeblaunch

    lazy val vndFujifilmFbDocuworks: MediaType =
      MediaType("application", "vnd.fujifilm.fb.docuworks", compressible = false, binary = true)
    
    lazy val `vnd.fujifilm.fb.docuworks`: MediaType = vndFujifilmFbDocuworks

    lazy val vndFujifilmFbDocuworksBinder: MediaType =
      MediaType("application", "vnd.fujifilm.fb.docuworks.binder", compressible = false, binary = true)
    
    lazy val `vnd.fujifilm.fb.docuworks.binder`: MediaType = vndFujifilmFbDocuworksBinder

    lazy val vndFujifilmFbDocuworksContainer: MediaType =
      MediaType("application", "vnd.fujifilm.fb.docuworks.container", compressible = false, binary = true)
    
    lazy val `vnd.fujifilm.fb.docuworks.container`: MediaType = vndFujifilmFbDocuworksContainer

    lazy val vndFujifilmFbJfiXml: MediaType =
      MediaType("application", "vnd.fujifilm.fb.jfi+xml", compressible = true, binary = true)
    
    lazy val `vnd.fujifilm.fb.jfi+xml`: MediaType = vndFujifilmFbJfiXml

    lazy val vndFujitsuOasys: MediaType =
      MediaType("application", "vnd.fujitsu.oasys", compressible = false, binary = true, fileExtensions = List("oas"))
    
    lazy val `vnd.fujitsu.oasys`: MediaType = vndFujitsuOasys

    lazy val vndFujitsuOasys2: MediaType =
      MediaType("application", "vnd.fujitsu.oasys2", compressible = false, binary = true, fileExtensions = List("oa2"))
    
    lazy val `vnd.fujitsu.oasys2`: MediaType = vndFujitsuOasys2

    lazy val vndFujitsuOasys3: MediaType =
      MediaType("application", "vnd.fujitsu.oasys3", compressible = false, binary = true, fileExtensions = List("oa3"))
    
    lazy val `vnd.fujitsu.oasys3`: MediaType = vndFujitsuOasys3

    lazy val vndFujitsuOasysgp: MediaType =
      MediaType("application", "vnd.fujitsu.oasysgp", compressible = false, binary = true, fileExtensions = List("fg5"))
    
    lazy val `vnd.fujitsu.oasysgp`: MediaType = vndFujitsuOasysgp

    lazy val vndFujitsuOasysprs: MediaType =
      MediaType("application", "vnd.fujitsu.oasysprs", compressible = false, binary = true, fileExtensions = List("bh2"))
    
    lazy val `vnd.fujitsu.oasysprs`: MediaType = vndFujitsuOasysprs

    lazy val vndFujixeroxArtEx: MediaType =
      MediaType("application", "vnd.fujixerox.art-ex", compressible = false, binary = true)
    
    lazy val `vnd.fujixerox.art-ex`: MediaType = vndFujixeroxArtEx

    lazy val vndFujixeroxArt4: MediaType =
      MediaType("application", "vnd.fujixerox.art4", compressible = false, binary = true)
    
    lazy val `vnd.fujixerox.art4`: MediaType = vndFujixeroxArt4

    lazy val vndFujixeroxDdd: MediaType =
      MediaType("application", "vnd.fujixerox.ddd", compressible = false, binary = true, fileExtensions = List("ddd"))
    
    lazy val `vnd.fujixerox.ddd`: MediaType = vndFujixeroxDdd

    lazy val vndFujixeroxDocuworks: MediaType =
      MediaType("application", "vnd.fujixerox.docuworks", compressible = false, binary = true, fileExtensions = List("xdw"))
    
    lazy val `vnd.fujixerox.docuworks`: MediaType = vndFujixeroxDocuworks

    lazy val vndFujixeroxDocuworksBinder: MediaType =
      MediaType("application", "vnd.fujixerox.docuworks.binder", compressible = false, binary = true, fileExtensions = List("xbd"))
    
    lazy val `vnd.fujixerox.docuworks.binder`: MediaType = vndFujixeroxDocuworksBinder

    lazy val vndFujixeroxDocuworksContainer: MediaType =
      MediaType("application", "vnd.fujixerox.docuworks.container", compressible = false, binary = true)
    
    lazy val `vnd.fujixerox.docuworks.container`: MediaType = vndFujixeroxDocuworksContainer

    lazy val vndFujixeroxHbpl: MediaType =
      MediaType("application", "vnd.fujixerox.hbpl", compressible = false, binary = true)
    
    lazy val `vnd.fujixerox.hbpl`: MediaType = vndFujixeroxHbpl

    lazy val vndFutMisnet: MediaType =
      MediaType("application", "vnd.fut-misnet", compressible = false, binary = true)
    
    lazy val `vnd.fut-misnet`: MediaType = vndFutMisnet

    lazy val vndFutoinCbor: MediaType =
      MediaType("application", "vnd.futoin+cbor", compressible = false, binary = true)
    
    lazy val `vnd.futoin+cbor`: MediaType = vndFutoinCbor

    lazy val vndFutoinJson: MediaType =
      MediaType("application", "vnd.futoin+json", compressible = true, binary = false)
    
    lazy val `vnd.futoin+json`: MediaType = vndFutoinJson

    lazy val vndFuzzysheet: MediaType =
      MediaType("application", "vnd.fuzzysheet", compressible = false, binary = true, fileExtensions = List("fzs"))
    
    lazy val `vnd.fuzzysheet`: MediaType = vndFuzzysheet

    lazy val vndG3pixG3fc: MediaType =
      MediaType("application", "vnd.g3pix.g3fc", compressible = false, binary = true)
    
    lazy val `vnd.g3pix.g3fc`: MediaType = vndG3pixG3fc

    lazy val vndGa4ghPassportJwt: MediaType =
      MediaType("application", "vnd.ga4gh.passport+jwt", compressible = false, binary = true)
    
    lazy val `vnd.ga4gh.passport+jwt`: MediaType = vndGa4ghPassportJwt

    lazy val vndGenomatixTuxedo: MediaType =
      MediaType("application", "vnd.genomatix.tuxedo", compressible = false, binary = true, fileExtensions = List("txd"))
    
    lazy val `vnd.genomatix.tuxedo`: MediaType = vndGenomatixTuxedo

    lazy val vndGenozip: MediaType =
      MediaType("application", "vnd.genozip", compressible = false, binary = true)
    
    lazy val `vnd.genozip`: MediaType = vndGenozip

    lazy val vndGenticsGrdJson: MediaType =
      MediaType("application", "vnd.gentics.grd+json", compressible = true, binary = false)
    
    lazy val `vnd.gentics.grd+json`: MediaType = vndGenticsGrdJson

    lazy val vndGentooCatmetadataXml: MediaType =
      MediaType("application", "vnd.gentoo.catmetadata+xml", compressible = true, binary = true)
    
    lazy val `vnd.gentoo.catmetadata+xml`: MediaType = vndGentooCatmetadataXml

    lazy val vndGentooEbuild: MediaType =
      MediaType("application", "vnd.gentoo.ebuild", compressible = false, binary = true)
    
    lazy val `vnd.gentoo.ebuild`: MediaType = vndGentooEbuild

    lazy val vndGentooEclass: MediaType =
      MediaType("application", "vnd.gentoo.eclass", compressible = false, binary = true)
    
    lazy val `vnd.gentoo.eclass`: MediaType = vndGentooEclass

    lazy val vndGentooGpkg: MediaType =
      MediaType("application", "vnd.gentoo.gpkg", compressible = false, binary = true)
    
    lazy val `vnd.gentoo.gpkg`: MediaType = vndGentooGpkg

    lazy val vndGentooManifest: MediaType =
      MediaType("application", "vnd.gentoo.manifest", compressible = false, binary = true)
    
    lazy val `vnd.gentoo.manifest`: MediaType = vndGentooManifest

    lazy val vndGentooPkgmetadataXml: MediaType =
      MediaType("application", "vnd.gentoo.pkgmetadata+xml", compressible = true, binary = true)
    
    lazy val `vnd.gentoo.pkgmetadata+xml`: MediaType = vndGentooPkgmetadataXml

    lazy val vndGentooXpak: MediaType =
      MediaType("application", "vnd.gentoo.xpak", compressible = false, binary = true)
    
    lazy val `vnd.gentoo.xpak`: MediaType = vndGentooXpak

    lazy val vndGeoJson: MediaType =
      MediaType("application", "vnd.geo+json", compressible = true, binary = false)
    
    lazy val `vnd.geo+json`: MediaType = vndGeoJson

    lazy val vndGeocubeXml: MediaType =
      MediaType("application", "vnd.geocube+xml", compressible = true, binary = true)
    
    lazy val `vnd.geocube+xml`: MediaType = vndGeocubeXml

    lazy val vndGeogebraFile: MediaType =
      MediaType("application", "vnd.geogebra.file", compressible = false, binary = true, fileExtensions = List("ggb"))
    
    lazy val `vnd.geogebra.file`: MediaType = vndGeogebraFile

    lazy val vndGeogebraPinboard: MediaType =
      MediaType("application", "vnd.geogebra.pinboard", compressible = false, binary = true)
    
    lazy val `vnd.geogebra.pinboard`: MediaType = vndGeogebraPinboard

    lazy val vndGeogebraSlides: MediaType =
      MediaType("application", "vnd.geogebra.slides", compressible = false, binary = true, fileExtensions = List("ggs"))
    
    lazy val `vnd.geogebra.slides`: MediaType = vndGeogebraSlides

    lazy val vndGeogebraTool: MediaType =
      MediaType("application", "vnd.geogebra.tool", compressible = false, binary = true, fileExtensions = List("ggt"))
    
    lazy val `vnd.geogebra.tool`: MediaType = vndGeogebraTool

    lazy val vndGeometryExplorer: MediaType =
      MediaType("application", "vnd.geometry-explorer", compressible = false, binary = true, fileExtensions = List("gex", "gre"))
    
    lazy val `vnd.geometry-explorer`: MediaType = vndGeometryExplorer

    lazy val vndGeonext: MediaType =
      MediaType("application", "vnd.geonext", compressible = false, binary = true, fileExtensions = List("gxt"))
    
    lazy val `vnd.geonext`: MediaType = vndGeonext

    lazy val vndGeoplan: MediaType =
      MediaType("application", "vnd.geoplan", compressible = false, binary = true, fileExtensions = List("g2w"))
    
    lazy val `vnd.geoplan`: MediaType = vndGeoplan

    lazy val vndGeospace: MediaType =
      MediaType("application", "vnd.geospace", compressible = false, binary = true, fileExtensions = List("g3w"))
    
    lazy val `vnd.geospace`: MediaType = vndGeospace

    lazy val vndGerber: MediaType =
      MediaType("application", "vnd.gerber", compressible = false, binary = true)
    
    lazy val `vnd.gerber`: MediaType = vndGerber

    lazy val vndGlobalplatformCardContentMgt: MediaType =
      MediaType("application", "vnd.globalplatform.card-content-mgt", compressible = false, binary = true)
    
    lazy val `vnd.globalplatform.card-content-mgt`: MediaType = vndGlobalplatformCardContentMgt

    lazy val vndGlobalplatformCardContentMgtResponse: MediaType =
      MediaType("application", "vnd.globalplatform.card-content-mgt-response", compressible = false, binary = true)
    
    lazy val `vnd.globalplatform.card-content-mgt-response`: MediaType = vndGlobalplatformCardContentMgtResponse

    lazy val vndGmx: MediaType =
      MediaType("application", "vnd.gmx", compressible = false, binary = true, fileExtensions = List("gmx"))
    
    lazy val `vnd.gmx`: MediaType = vndGmx

    lazy val vndGnuTalerExchangeJson: MediaType =
      MediaType("application", "vnd.gnu.taler.exchange+json", compressible = true, binary = false)
    
    lazy val `vnd.gnu.taler.exchange+json`: MediaType = vndGnuTalerExchangeJson

    lazy val vndGnuTalerMerchantJson: MediaType =
      MediaType("application", "vnd.gnu.taler.merchant+json", compressible = true, binary = false)
    
    lazy val `vnd.gnu.taler.merchant+json`: MediaType = vndGnuTalerMerchantJson

    lazy val vndGoogleAppsAudio: MediaType =
      MediaType("application", "vnd.google-apps.audio", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.audio`: MediaType = vndGoogleAppsAudio

    lazy val vndGoogleAppsDocument: MediaType =
      MediaType("application", "vnd.google-apps.document", compressible = false, binary = true, fileExtensions = List("gdoc"))
    
    lazy val `vnd.google-apps.document`: MediaType = vndGoogleAppsDocument

    lazy val vndGoogleAppsDrawing: MediaType =
      MediaType("application", "vnd.google-apps.drawing", compressible = false, binary = true, fileExtensions = List("gdraw"))
    
    lazy val `vnd.google-apps.drawing`: MediaType = vndGoogleAppsDrawing

    lazy val vndGoogleAppsDriveSdk: MediaType =
      MediaType("application", "vnd.google-apps.drive-sdk", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.drive-sdk`: MediaType = vndGoogleAppsDriveSdk

    lazy val vndGoogleAppsFile: MediaType =
      MediaType("application", "vnd.google-apps.file", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.file`: MediaType = vndGoogleAppsFile

    lazy val vndGoogleAppsFolder: MediaType =
      MediaType("application", "vnd.google-apps.folder", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.folder`: MediaType = vndGoogleAppsFolder

    lazy val vndGoogleAppsForm: MediaType =
      MediaType("application", "vnd.google-apps.form", compressible = false, binary = true, fileExtensions = List("gform"))
    
    lazy val `vnd.google-apps.form`: MediaType = vndGoogleAppsForm

    lazy val vndGoogleAppsFusiontable: MediaType =
      MediaType("application", "vnd.google-apps.fusiontable", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.fusiontable`: MediaType = vndGoogleAppsFusiontable

    lazy val vndGoogleAppsJam: MediaType =
      MediaType("application", "vnd.google-apps.jam", compressible = false, binary = true, fileExtensions = List("gjam"))
    
    lazy val `vnd.google-apps.jam`: MediaType = vndGoogleAppsJam

    lazy val vndGoogleAppsMailLayout: MediaType =
      MediaType("application", "vnd.google-apps.mail-layout", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.mail-layout`: MediaType = vndGoogleAppsMailLayout

    lazy val vndGoogleAppsMap: MediaType =
      MediaType("application", "vnd.google-apps.map", compressible = false, binary = true, fileExtensions = List("gmap"))
    
    lazy val `vnd.google-apps.map`: MediaType = vndGoogleAppsMap

    lazy val vndGoogleAppsPhoto: MediaType =
      MediaType("application", "vnd.google-apps.photo", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.photo`: MediaType = vndGoogleAppsPhoto

    lazy val vndGoogleAppsPresentation: MediaType =
      MediaType("application", "vnd.google-apps.presentation", compressible = false, binary = true, fileExtensions = List("gslides"))
    
    lazy val `vnd.google-apps.presentation`: MediaType = vndGoogleAppsPresentation

    lazy val vndGoogleAppsScript: MediaType =
      MediaType("application", "vnd.google-apps.script", compressible = false, binary = true, fileExtensions = List("gscript"))
    
    lazy val `vnd.google-apps.script`: MediaType = vndGoogleAppsScript

    lazy val vndGoogleAppsShortcut: MediaType =
      MediaType("application", "vnd.google-apps.shortcut", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.shortcut`: MediaType = vndGoogleAppsShortcut

    lazy val vndGoogleAppsSite: MediaType =
      MediaType("application", "vnd.google-apps.site", compressible = false, binary = true, fileExtensions = List("gsite"))
    
    lazy val `vnd.google-apps.site`: MediaType = vndGoogleAppsSite

    lazy val vndGoogleAppsSpreadsheet: MediaType =
      MediaType("application", "vnd.google-apps.spreadsheet", compressible = false, binary = true, fileExtensions = List("gsheet"))
    
    lazy val `vnd.google-apps.spreadsheet`: MediaType = vndGoogleAppsSpreadsheet

    lazy val vndGoogleAppsUnknown: MediaType =
      MediaType("application", "vnd.google-apps.unknown", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.unknown`: MediaType = vndGoogleAppsUnknown

    lazy val vndGoogleAppsVideo: MediaType =
      MediaType("application", "vnd.google-apps.video", compressible = false, binary = true)
    
    lazy val `vnd.google-apps.video`: MediaType = vndGoogleAppsVideo

    lazy val vndGoogleEarthKmlXml: MediaType =
      MediaType("application", "vnd.google-earth.kml+xml", compressible = true, binary = true, fileExtensions = List("kml"))
    
    lazy val `vnd.google-earth.kml+xml`: MediaType = vndGoogleEarthKmlXml

    lazy val vndGoogleEarthKmz: MediaType =
      MediaType("application", "vnd.google-earth.kmz", compressible = false, binary = true, fileExtensions = List("kmz"))
    
    lazy val `vnd.google-earth.kmz`: MediaType = vndGoogleEarthKmz

    lazy val vndGovSkEFormXml: MediaType =
      MediaType("application", "vnd.gov.sk.e-form+xml", compressible = true, binary = true)
    
    lazy val `vnd.gov.sk.e-form+xml`: MediaType = vndGovSkEFormXml

    lazy val vndGovSkEFormZip: MediaType =
      MediaType("application", "vnd.gov.sk.e-form+zip", compressible = false, binary = true)
    
    lazy val `vnd.gov.sk.e-form+zip`: MediaType = vndGovSkEFormZip

    lazy val vndGovSkXmldatacontainerXml: MediaType =
      MediaType("application", "vnd.gov.sk.xmldatacontainer+xml", compressible = true, binary = true, fileExtensions = List("xdcf"))
    
    lazy val `vnd.gov.sk.xmldatacontainer+xml`: MediaType = vndGovSkXmldatacontainerXml

    lazy val vndGpxseeMapXml: MediaType =
      MediaType("application", "vnd.gpxsee.map+xml", compressible = true, binary = true)
    
    lazy val `vnd.gpxsee.map+xml`: MediaType = vndGpxseeMapXml

    lazy val vndGrafeq: MediaType =
      MediaType("application", "vnd.grafeq", compressible = false, binary = true, fileExtensions = List("gqf", "gqs"))
    
    lazy val `vnd.grafeq`: MediaType = vndGrafeq

    lazy val vndGridmp: MediaType =
      MediaType("application", "vnd.gridmp", compressible = false, binary = true)
    
    lazy val `vnd.gridmp`: MediaType = vndGridmp

    lazy val vndGrooveAccount: MediaType =
      MediaType("application", "vnd.groove-account", compressible = false, binary = true, fileExtensions = List("gac"))
    
    lazy val `vnd.groove-account`: MediaType = vndGrooveAccount

    lazy val vndGrooveHelp: MediaType =
      MediaType("application", "vnd.groove-help", compressible = false, binary = true, fileExtensions = List("ghf"))
    
    lazy val `vnd.groove-help`: MediaType = vndGrooveHelp

    lazy val vndGrooveIdentityMessage: MediaType =
      MediaType("application", "vnd.groove-identity-message", compressible = false, binary = true, fileExtensions = List("gim"))
    
    lazy val `vnd.groove-identity-message`: MediaType = vndGrooveIdentityMessage

    lazy val vndGrooveInjector: MediaType =
      MediaType("application", "vnd.groove-injector", compressible = false, binary = true, fileExtensions = List("grv"))
    
    lazy val `vnd.groove-injector`: MediaType = vndGrooveInjector

    lazy val vndGrooveToolMessage: MediaType =
      MediaType("application", "vnd.groove-tool-message", compressible = false, binary = true, fileExtensions = List("gtm"))
    
    lazy val `vnd.groove-tool-message`: MediaType = vndGrooveToolMessage

    lazy val vndGrooveToolTemplate: MediaType =
      MediaType("application", "vnd.groove-tool-template", compressible = false, binary = true, fileExtensions = List("tpl"))
    
    lazy val `vnd.groove-tool-template`: MediaType = vndGrooveToolTemplate

    lazy val vndGrooveVcard: MediaType =
      MediaType("application", "vnd.groove-vcard", compressible = false, binary = true, fileExtensions = List("vcg"))
    
    lazy val `vnd.groove-vcard`: MediaType = vndGrooveVcard

    lazy val vndHalJson: MediaType =
      MediaType("application", "vnd.hal+json", compressible = true, binary = false)
    
    lazy val `vnd.hal+json`: MediaType = vndHalJson

    lazy val vndHalXml: MediaType =
      MediaType("application", "vnd.hal+xml", compressible = true, binary = true, fileExtensions = List("hal"))
    
    lazy val `vnd.hal+xml`: MediaType = vndHalXml

    lazy val vndHandheldEntertainmentXml: MediaType =
      MediaType("application", "vnd.handheld-entertainment+xml", compressible = true, binary = true, fileExtensions = List("zmm"))
    
    lazy val `vnd.handheld-entertainment+xml`: MediaType = vndHandheldEntertainmentXml

    lazy val vndHbci: MediaType =
      MediaType("application", "vnd.hbci", compressible = false, binary = true, fileExtensions = List("hbci"))
    
    lazy val `vnd.hbci`: MediaType = vndHbci

    lazy val vndHcJson: MediaType =
      MediaType("application", "vnd.hc+json", compressible = true, binary = false)
    
    lazy val `vnd.hc+json`: MediaType = vndHcJson

    lazy val vndHclBireports: MediaType =
      MediaType("application", "vnd.hcl-bireports", compressible = false, binary = true)
    
    lazy val `vnd.hcl-bireports`: MediaType = vndHclBireports

    lazy val vndHdt: MediaType =
      MediaType("application", "vnd.hdt", compressible = false, binary = true)
    
    lazy val `vnd.hdt`: MediaType = vndHdt

    lazy val vndHerokuJson: MediaType =
      MediaType("application", "vnd.heroku+json", compressible = true, binary = false)
    
    lazy val `vnd.heroku+json`: MediaType = vndHerokuJson

    lazy val vndHheLessonPlayer: MediaType =
      MediaType("application", "vnd.hhe.lesson-player", compressible = false, binary = true, fileExtensions = List("les"))
    
    lazy val `vnd.hhe.lesson-player`: MediaType = vndHheLessonPlayer

    lazy val vndHpHpgl: MediaType =
      MediaType("application", "vnd.hp-hpgl", compressible = false, binary = true, fileExtensions = List("hpgl"))
    
    lazy val `vnd.hp-hpgl`: MediaType = vndHpHpgl

    lazy val vndHpHpid: MediaType =
      MediaType("application", "vnd.hp-hpid", compressible = false, binary = true, fileExtensions = List("hpid"))
    
    lazy val `vnd.hp-hpid`: MediaType = vndHpHpid

    lazy val vndHpHps: MediaType =
      MediaType("application", "vnd.hp-hps", compressible = false, binary = true, fileExtensions = List("hps"))
    
    lazy val `vnd.hp-hps`: MediaType = vndHpHps

    lazy val vndHpJlyt: MediaType =
      MediaType("application", "vnd.hp-jlyt", compressible = false, binary = true, fileExtensions = List("jlt"))
    
    lazy val `vnd.hp-jlyt`: MediaType = vndHpJlyt

    lazy val vndHpPcl: MediaType =
      MediaType("application", "vnd.hp-pcl", compressible = false, binary = true, fileExtensions = List("pcl"))
    
    lazy val `vnd.hp-pcl`: MediaType = vndHpPcl

    lazy val vndHpPclxl: MediaType =
      MediaType("application", "vnd.hp-pclxl", compressible = false, binary = true, fileExtensions = List("pclxl"))
    
    lazy val `vnd.hp-pclxl`: MediaType = vndHpPclxl

    lazy val vndHsl: MediaType =
      MediaType("application", "vnd.hsl", compressible = false, binary = true)
    
    lazy val `vnd.hsl`: MediaType = vndHsl

    lazy val vndHttphone: MediaType =
      MediaType("application", "vnd.httphone", compressible = false, binary = true)
    
    lazy val `vnd.httphone`: MediaType = vndHttphone

    lazy val vndHydrostatixSofData: MediaType =
      MediaType("application", "vnd.hydrostatix.sof-data", compressible = false, binary = true, fileExtensions = List("sfd-hdstx"))
    
    lazy val `vnd.hydrostatix.sof-data`: MediaType = vndHydrostatixSofData

    lazy val vndHyperJson: MediaType =
      MediaType("application", "vnd.hyper+json", compressible = true, binary = false)
    
    lazy val `vnd.hyper+json`: MediaType = vndHyperJson

    lazy val vndHyperItemJson: MediaType =
      MediaType("application", "vnd.hyper-item+json", compressible = true, binary = false)
    
    lazy val `vnd.hyper-item+json`: MediaType = vndHyperItemJson

    lazy val vndHyperdriveJson: MediaType =
      MediaType("application", "vnd.hyperdrive+json", compressible = true, binary = false)
    
    lazy val `vnd.hyperdrive+json`: MediaType = vndHyperdriveJson

    lazy val vndHzn3dCrossword: MediaType =
      MediaType("application", "vnd.hzn-3d-crossword", compressible = false, binary = true)
    
    lazy val `vnd.hzn-3d-crossword`: MediaType = vndHzn3dCrossword

    lazy val vndIbmAfplinedata: MediaType =
      MediaType("application", "vnd.ibm.afplinedata", compressible = false, binary = true)
    
    lazy val `vnd.ibm.afplinedata`: MediaType = vndIbmAfplinedata

    lazy val vndIbmElectronicMedia: MediaType =
      MediaType("application", "vnd.ibm.electronic-media", compressible = false, binary = true)
    
    lazy val `vnd.ibm.electronic-media`: MediaType = vndIbmElectronicMedia

    lazy val vndIbmMinipay: MediaType =
      MediaType("application", "vnd.ibm.minipay", compressible = false, binary = true, fileExtensions = List("mpy"))
    
    lazy val `vnd.ibm.minipay`: MediaType = vndIbmMinipay

    lazy val vndIbmModcap: MediaType =
      MediaType("application", "vnd.ibm.modcap", compressible = false, binary = true, fileExtensions = List("afp", "listafp", "list3820"))
    
    lazy val `vnd.ibm.modcap`: MediaType = vndIbmModcap

    lazy val vndIbmRightsManagement: MediaType =
      MediaType("application", "vnd.ibm.rights-management", compressible = false, binary = true, fileExtensions = List("irm"))
    
    lazy val `vnd.ibm.rights-management`: MediaType = vndIbmRightsManagement

    lazy val vndIbmSecureContainer: MediaType =
      MediaType("application", "vnd.ibm.secure-container", compressible = false, binary = true, fileExtensions = List("sc"))
    
    lazy val `vnd.ibm.secure-container`: MediaType = vndIbmSecureContainer

    lazy val vndIccprofile: MediaType =
      MediaType("application", "vnd.iccprofile", compressible = false, binary = true, fileExtensions = List("icc", "icm"))
    
    lazy val `vnd.iccprofile`: MediaType = vndIccprofile

    lazy val vndIeee1905: MediaType =
      MediaType("application", "vnd.ieee.1905", compressible = false, binary = true)
    
    lazy val `vnd.ieee.1905`: MediaType = vndIeee1905

    lazy val vndIgloader: MediaType =
      MediaType("application", "vnd.igloader", compressible = false, binary = true, fileExtensions = List("igl"))
    
    lazy val `vnd.igloader`: MediaType = vndIgloader

    lazy val vndImagemeterFolderZip: MediaType =
      MediaType("application", "vnd.imagemeter.folder+zip", compressible = false, binary = true)
    
    lazy val `vnd.imagemeter.folder+zip`: MediaType = vndImagemeterFolderZip

    lazy val vndImagemeterImageZip: MediaType =
      MediaType("application", "vnd.imagemeter.image+zip", compressible = false, binary = true)
    
    lazy val `vnd.imagemeter.image+zip`: MediaType = vndImagemeterImageZip

    lazy val vndImmervisionIvp: MediaType =
      MediaType("application", "vnd.immervision-ivp", compressible = false, binary = true, fileExtensions = List("ivp"))
    
    lazy val `vnd.immervision-ivp`: MediaType = vndImmervisionIvp

    lazy val vndImmervisionIvu: MediaType =
      MediaType("application", "vnd.immervision-ivu", compressible = false, binary = true, fileExtensions = List("ivu"))
    
    lazy val `vnd.immervision-ivu`: MediaType = vndImmervisionIvu

    lazy val vndImsImsccv1p1: MediaType =
      MediaType("application", "vnd.ims.imsccv1p1", compressible = false, binary = true)
    
    lazy val `vnd.ims.imsccv1p1`: MediaType = vndImsImsccv1p1

    lazy val vndImsImsccv1p2: MediaType =
      MediaType("application", "vnd.ims.imsccv1p2", compressible = false, binary = true)
    
    lazy val `vnd.ims.imsccv1p2`: MediaType = vndImsImsccv1p2

    lazy val vndImsImsccv1p3: MediaType =
      MediaType("application", "vnd.ims.imsccv1p3", compressible = false, binary = true)
    
    lazy val `vnd.ims.imsccv1p3`: MediaType = vndImsImsccv1p3

    lazy val vndImsLisV2ResultJson: MediaType =
      MediaType("application", "vnd.ims.lis.v2.result+json", compressible = true, binary = false)
    
    lazy val `vnd.ims.lis.v2.result+json`: MediaType = vndImsLisV2ResultJson

    lazy val vndImsLtiV2ToolconsumerprofileJson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolconsumerprofile+json", compressible = true, binary = false)
    
    lazy val `vnd.ims.lti.v2.toolconsumerprofile+json`: MediaType = vndImsLtiV2ToolconsumerprofileJson

    lazy val vndImsLtiV2ToolproxyJson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolproxy+json", compressible = true, binary = false)
    
    lazy val `vnd.ims.lti.v2.toolproxy+json`: MediaType = vndImsLtiV2ToolproxyJson

    lazy val vndImsLtiV2ToolproxyIdJson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolproxy.id+json", compressible = true, binary = false)
    
    lazy val `vnd.ims.lti.v2.toolproxy.id+json`: MediaType = vndImsLtiV2ToolproxyIdJson

    lazy val vndImsLtiV2ToolsettingsJson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolsettings+json", compressible = true, binary = false)
    
    lazy val `vnd.ims.lti.v2.toolsettings+json`: MediaType = vndImsLtiV2ToolsettingsJson

    lazy val vndImsLtiV2ToolsettingsSimpleJson: MediaType =
      MediaType("application", "vnd.ims.lti.v2.toolsettings.simple+json", compressible = true, binary = false)
    
    lazy val `vnd.ims.lti.v2.toolsettings.simple+json`: MediaType = vndImsLtiV2ToolsettingsSimpleJson

    lazy val vndInformedcontrolRmsXml: MediaType =
      MediaType("application", "vnd.informedcontrol.rms+xml", compressible = true, binary = true)
    
    lazy val `vnd.informedcontrol.rms+xml`: MediaType = vndInformedcontrolRmsXml

    lazy val vndInformixVisionary: MediaType =
      MediaType("application", "vnd.informix-visionary", compressible = false, binary = true)
    
    lazy val `vnd.informix-visionary`: MediaType = vndInformixVisionary

    lazy val vndInfotechProject: MediaType =
      MediaType("application", "vnd.infotech.project", compressible = false, binary = true)
    
    lazy val `vnd.infotech.project`: MediaType = vndInfotechProject

    lazy val vndInfotechProjectXml: MediaType =
      MediaType("application", "vnd.infotech.project+xml", compressible = true, binary = true)
    
    lazy val `vnd.infotech.project+xml`: MediaType = vndInfotechProjectXml

    lazy val vndInnopathWampNotification: MediaType =
      MediaType("application", "vnd.innopath.wamp.notification", compressible = false, binary = true)
    
    lazy val `vnd.innopath.wamp.notification`: MediaType = vndInnopathWampNotification

    lazy val vndInsorsIgm: MediaType =
      MediaType("application", "vnd.insors.igm", compressible = false, binary = true, fileExtensions = List("igm"))
    
    lazy val `vnd.insors.igm`: MediaType = vndInsorsIgm

    lazy val vndInterconFormnet: MediaType =
      MediaType("application", "vnd.intercon.formnet", compressible = false, binary = true, fileExtensions = List("xpw", "xpx"))
    
    lazy val `vnd.intercon.formnet`: MediaType = vndInterconFormnet

    lazy val vndIntergeo: MediaType =
      MediaType("application", "vnd.intergeo", compressible = false, binary = true, fileExtensions = List("i2g"))
    
    lazy val `vnd.intergeo`: MediaType = vndIntergeo

    lazy val vndIntertrustDigibox: MediaType =
      MediaType("application", "vnd.intertrust.digibox", compressible = false, binary = true)
    
    lazy val `vnd.intertrust.digibox`: MediaType = vndIntertrustDigibox

    lazy val vndIntertrustNncp: MediaType =
      MediaType("application", "vnd.intertrust.nncp", compressible = false, binary = true)
    
    lazy val `vnd.intertrust.nncp`: MediaType = vndIntertrustNncp

    lazy val vndIntuQbo: MediaType =
      MediaType("application", "vnd.intu.qbo", compressible = false, binary = true, fileExtensions = List("qbo"))
    
    lazy val `vnd.intu.qbo`: MediaType = vndIntuQbo

    lazy val vndIntuQfx: MediaType =
      MediaType("application", "vnd.intu.qfx", compressible = false, binary = true, fileExtensions = List("qfx"))
    
    lazy val `vnd.intu.qfx`: MediaType = vndIntuQfx

    lazy val vndIpfsIpnsRecord: MediaType =
      MediaType("application", "vnd.ipfs.ipns-record", compressible = false, binary = true)
    
    lazy val `vnd.ipfs.ipns-record`: MediaType = vndIpfsIpnsRecord

    lazy val vndIpldCar: MediaType =
      MediaType("application", "vnd.ipld.car", compressible = false, binary = true)
    
    lazy val `vnd.ipld.car`: MediaType = vndIpldCar

    lazy val vndIpldDagCbor: MediaType =
      MediaType("application", "vnd.ipld.dag-cbor", compressible = false, binary = true)
    
    lazy val `vnd.ipld.dag-cbor`: MediaType = vndIpldDagCbor

    lazy val vndIpldDagJson: MediaType =
      MediaType("application", "vnd.ipld.dag-json", compressible = false, binary = false)
    
    lazy val `vnd.ipld.dag-json`: MediaType = vndIpldDagJson

    lazy val vndIpldRaw: MediaType =
      MediaType("application", "vnd.ipld.raw", compressible = false, binary = true)
    
    lazy val `vnd.ipld.raw`: MediaType = vndIpldRaw

    lazy val vndIptcG2CatalogitemXml: MediaType =
      MediaType("application", "vnd.iptc.g2.catalogitem+xml", compressible = true, binary = true)
    
    lazy val `vnd.iptc.g2.catalogitem+xml`: MediaType = vndIptcG2CatalogitemXml

    lazy val vndIptcG2ConceptitemXml: MediaType =
      MediaType("application", "vnd.iptc.g2.conceptitem+xml", compressible = true, binary = true)
    
    lazy val `vnd.iptc.g2.conceptitem+xml`: MediaType = vndIptcG2ConceptitemXml

    lazy val vndIptcG2KnowledgeitemXml: MediaType =
      MediaType("application", "vnd.iptc.g2.knowledgeitem+xml", compressible = true, binary = true)
    
    lazy val `vnd.iptc.g2.knowledgeitem+xml`: MediaType = vndIptcG2KnowledgeitemXml

    lazy val vndIptcG2NewsitemXml: MediaType =
      MediaType("application", "vnd.iptc.g2.newsitem+xml", compressible = true, binary = true)
    
    lazy val `vnd.iptc.g2.newsitem+xml`: MediaType = vndIptcG2NewsitemXml

    lazy val vndIptcG2NewsmessageXml: MediaType =
      MediaType("application", "vnd.iptc.g2.newsmessage+xml", compressible = true, binary = true)
    
    lazy val `vnd.iptc.g2.newsmessage+xml`: MediaType = vndIptcG2NewsmessageXml

    lazy val vndIptcG2PackageitemXml: MediaType =
      MediaType("application", "vnd.iptc.g2.packageitem+xml", compressible = true, binary = true)
    
    lazy val `vnd.iptc.g2.packageitem+xml`: MediaType = vndIptcG2PackageitemXml

    lazy val vndIptcG2PlanningitemXml: MediaType =
      MediaType("application", "vnd.iptc.g2.planningitem+xml", compressible = true, binary = true)
    
    lazy val `vnd.iptc.g2.planningitem+xml`: MediaType = vndIptcG2PlanningitemXml

    lazy val vndIpunpluggedRcprofile: MediaType =
      MediaType("application", "vnd.ipunplugged.rcprofile", compressible = false, binary = true, fileExtensions = List("rcprofile"))
    
    lazy val `vnd.ipunplugged.rcprofile`: MediaType = vndIpunpluggedRcprofile

    lazy val vndIrepositoryPackageXml: MediaType =
      MediaType("application", "vnd.irepository.package+xml", compressible = true, binary = true, fileExtensions = List("irp"))
    
    lazy val `vnd.irepository.package+xml`: MediaType = vndIrepositoryPackageXml

    lazy val vndIsXpr: MediaType =
      MediaType("application", "vnd.is-xpr", compressible = false, binary = true, fileExtensions = List("xpr"))
    
    lazy val `vnd.is-xpr`: MediaType = vndIsXpr

    lazy val vndIsacFcs: MediaType =
      MediaType("application", "vnd.isac.fcs", compressible = false, binary = true, fileExtensions = List("fcs"))
    
    lazy val `vnd.isac.fcs`: MediaType = vndIsacFcs

    lazy val vndIso1178310Zip: MediaType =
      MediaType("application", "vnd.iso11783-10+zip", compressible = false, binary = true)
    
    lazy val `vnd.iso11783-10+zip`: MediaType = vndIso1178310Zip

    lazy val vndJam: MediaType =
      MediaType("application", "vnd.jam", compressible = false, binary = true, fileExtensions = List("jam"))
    
    lazy val `vnd.jam`: MediaType = vndJam

    lazy val vndJapannetDirectoryService: MediaType =
      MediaType("application", "vnd.japannet-directory-service", compressible = false, binary = true)
    
    lazy val `vnd.japannet-directory-service`: MediaType = vndJapannetDirectoryService

    lazy val vndJapannetJpnstoreWakeup: MediaType =
      MediaType("application", "vnd.japannet-jpnstore-wakeup", compressible = false, binary = true)
    
    lazy val `vnd.japannet-jpnstore-wakeup`: MediaType = vndJapannetJpnstoreWakeup

    lazy val vndJapannetPaymentWakeup: MediaType =
      MediaType("application", "vnd.japannet-payment-wakeup", compressible = false, binary = true)
    
    lazy val `vnd.japannet-payment-wakeup`: MediaType = vndJapannetPaymentWakeup

    lazy val vndJapannetRegistration: MediaType =
      MediaType("application", "vnd.japannet-registration", compressible = false, binary = true)
    
    lazy val `vnd.japannet-registration`: MediaType = vndJapannetRegistration

    lazy val vndJapannetRegistrationWakeup: MediaType =
      MediaType("application", "vnd.japannet-registration-wakeup", compressible = false, binary = true)
    
    lazy val `vnd.japannet-registration-wakeup`: MediaType = vndJapannetRegistrationWakeup

    lazy val vndJapannetSetstoreWakeup: MediaType =
      MediaType("application", "vnd.japannet-setstore-wakeup", compressible = false, binary = true)
    
    lazy val `vnd.japannet-setstore-wakeup`: MediaType = vndJapannetSetstoreWakeup

    lazy val vndJapannetVerification: MediaType =
      MediaType("application", "vnd.japannet-verification", compressible = false, binary = true)
    
    lazy val `vnd.japannet-verification`: MediaType = vndJapannetVerification

    lazy val vndJapannetVerificationWakeup: MediaType =
      MediaType("application", "vnd.japannet-verification-wakeup", compressible = false, binary = true)
    
    lazy val `vnd.japannet-verification-wakeup`: MediaType = vndJapannetVerificationWakeup

    lazy val vndJcpJavameMidletRms: MediaType =
      MediaType("application", "vnd.jcp.javame.midlet-rms", compressible = false, binary = true, fileExtensions = List("rms"))
    
    lazy val `vnd.jcp.javame.midlet-rms`: MediaType = vndJcpJavameMidletRms

    lazy val vndJisp: MediaType =
      MediaType("application", "vnd.jisp", compressible = false, binary = true, fileExtensions = List("jisp"))
    
    lazy val `vnd.jisp`: MediaType = vndJisp

    lazy val vndJoostJodaArchive: MediaType =
      MediaType("application", "vnd.joost.joda-archive", compressible = false, binary = true, fileExtensions = List("joda"))
    
    lazy val `vnd.joost.joda-archive`: MediaType = vndJoostJodaArchive

    lazy val vndJskIsdnNgn: MediaType =
      MediaType("application", "vnd.jsk.isdn-ngn", compressible = false, binary = true)
    
    lazy val `vnd.jsk.isdn-ngn`: MediaType = vndJskIsdnNgn

    lazy val vndKahootz: MediaType =
      MediaType("application", "vnd.kahootz", compressible = false, binary = true, fileExtensions = List("ktz", "ktr"))
    
    lazy val `vnd.kahootz`: MediaType = vndKahootz

    lazy val vndKdeKarbon: MediaType =
      MediaType("application", "vnd.kde.karbon", compressible = false, binary = true, fileExtensions = List("karbon"))
    
    lazy val `vnd.kde.karbon`: MediaType = vndKdeKarbon

    lazy val vndKdeKchart: MediaType =
      MediaType("application", "vnd.kde.kchart", compressible = false, binary = true, fileExtensions = List("chrt"))
    
    lazy val `vnd.kde.kchart`: MediaType = vndKdeKchart

    lazy val vndKdeKformula: MediaType =
      MediaType("application", "vnd.kde.kformula", compressible = false, binary = true, fileExtensions = List("kfo"))
    
    lazy val `vnd.kde.kformula`: MediaType = vndKdeKformula

    lazy val vndKdeKivio: MediaType =
      MediaType("application", "vnd.kde.kivio", compressible = false, binary = true, fileExtensions = List("flw"))
    
    lazy val `vnd.kde.kivio`: MediaType = vndKdeKivio

    lazy val vndKdeKontour: MediaType =
      MediaType("application", "vnd.kde.kontour", compressible = false, binary = true, fileExtensions = List("kon"))
    
    lazy val `vnd.kde.kontour`: MediaType = vndKdeKontour

    lazy val vndKdeKpresenter: MediaType =
      MediaType("application", "vnd.kde.kpresenter", compressible = false, binary = true, fileExtensions = List("kpr", "kpt"))
    
    lazy val `vnd.kde.kpresenter`: MediaType = vndKdeKpresenter

    lazy val vndKdeKspread: MediaType =
      MediaType("application", "vnd.kde.kspread", compressible = false, binary = true, fileExtensions = List("ksp"))
    
    lazy val `vnd.kde.kspread`: MediaType = vndKdeKspread

    lazy val vndKdeKword: MediaType =
      MediaType("application", "vnd.kde.kword", compressible = false, binary = true, fileExtensions = List("kwd", "kwt"))
    
    lazy val `vnd.kde.kword`: MediaType = vndKdeKword

    lazy val vndKdl: MediaType =
      MediaType("application", "vnd.kdl", compressible = false, binary = true)
    
    lazy val `vnd.kdl`: MediaType = vndKdl

    lazy val vndKenameaapp: MediaType =
      MediaType("application", "vnd.kenameaapp", compressible = false, binary = true, fileExtensions = List("htke"))
    
    lazy val `vnd.kenameaapp`: MediaType = vndKenameaapp

    lazy val vndKeymanKmpZip: MediaType =
      MediaType("application", "vnd.keyman.kmp+zip", compressible = false, binary = true)
    
    lazy val `vnd.keyman.kmp+zip`: MediaType = vndKeymanKmpZip

    lazy val vndKeymanKmx: MediaType =
      MediaType("application", "vnd.keyman.kmx", compressible = false, binary = true)
    
    lazy val `vnd.keyman.kmx`: MediaType = vndKeymanKmx

    lazy val vndKidspiration: MediaType =
      MediaType("application", "vnd.kidspiration", compressible = false, binary = true, fileExtensions = List("kia"))
    
    lazy val `vnd.kidspiration`: MediaType = vndKidspiration

    lazy val vndKinar: MediaType =
      MediaType("application", "vnd.kinar", compressible = false, binary = true, fileExtensions = List("kne", "knp"))
    
    lazy val `vnd.kinar`: MediaType = vndKinar

    lazy val vndKoan: MediaType =
      MediaType("application", "vnd.koan", compressible = false, binary = true, fileExtensions = List("skp", "skd", "skt", "skm"))
    
    lazy val `vnd.koan`: MediaType = vndKoan

    lazy val vndKodakDescriptor: MediaType =
      MediaType("application", "vnd.kodak-descriptor", compressible = false, binary = true, fileExtensions = List("sse"))
    
    lazy val `vnd.kodak-descriptor`: MediaType = vndKodakDescriptor

    lazy val vndLas: MediaType =
      MediaType("application", "vnd.las", compressible = false, binary = true)
    
    lazy val `vnd.las`: MediaType = vndLas

    lazy val vndLasLasJson: MediaType =
      MediaType("application", "vnd.las.las+json", compressible = true, binary = false)
    
    lazy val `vnd.las.las+json`: MediaType = vndLasLasJson

    lazy val vndLasLasXml: MediaType =
      MediaType("application", "vnd.las.las+xml", compressible = true, binary = true, fileExtensions = List("lasxml"))
    
    lazy val `vnd.las.las+xml`: MediaType = vndLasLasXml

    lazy val vndLaszip: MediaType =
      MediaType("application", "vnd.laszip", compressible = false, binary = true)
    
    lazy val `vnd.laszip`: MediaType = vndLaszip

    lazy val vndLdevProductlicensing: MediaType =
      MediaType("application", "vnd.ldev.productlicensing", compressible = false, binary = true)
    
    lazy val `vnd.ldev.productlicensing`: MediaType = vndLdevProductlicensing

    lazy val vndLeapJson: MediaType =
      MediaType("application", "vnd.leap+json", compressible = true, binary = false)
    
    lazy val `vnd.leap+json`: MediaType = vndLeapJson

    lazy val vndLibertyRequestXml: MediaType =
      MediaType("application", "vnd.liberty-request+xml", compressible = true, binary = true)
    
    lazy val `vnd.liberty-request+xml`: MediaType = vndLibertyRequestXml

    lazy val vndLlamagraphicsLifeBalanceDesktop: MediaType =
      MediaType("application", "vnd.llamagraphics.life-balance.desktop", compressible = false, binary = true, fileExtensions = List("lbd"))
    
    lazy val `vnd.llamagraphics.life-balance.desktop`: MediaType = vndLlamagraphicsLifeBalanceDesktop

    lazy val vndLlamagraphicsLifeBalanceExchangeXml: MediaType =
      MediaType("application", "vnd.llamagraphics.life-balance.exchange+xml", compressible = true, binary = true, fileExtensions = List("lbe"))
    
    lazy val `vnd.llamagraphics.life-balance.exchange+xml`: MediaType = vndLlamagraphicsLifeBalanceExchangeXml

    lazy val vndLogipipeCircuitZip: MediaType =
      MediaType("application", "vnd.logipipe.circuit+zip", compressible = false, binary = true)
    
    lazy val `vnd.logipipe.circuit+zip`: MediaType = vndLogipipeCircuitZip

    lazy val vndLoom: MediaType =
      MediaType("application", "vnd.loom", compressible = false, binary = true)
    
    lazy val `vnd.loom`: MediaType = vndLoom

    lazy val vndLotus123: MediaType =
      MediaType("application", "vnd.lotus-1-2-3", compressible = false, binary = true, fileExtensions = List("123"))
    
    lazy val `vnd.lotus-1-2-3`: MediaType = vndLotus123

    lazy val vndLotusApproach: MediaType =
      MediaType("application", "vnd.lotus-approach", compressible = false, binary = true, fileExtensions = List("apr"))
    
    lazy val `vnd.lotus-approach`: MediaType = vndLotusApproach

    lazy val vndLotusFreelance: MediaType =
      MediaType("application", "vnd.lotus-freelance", compressible = false, binary = true, fileExtensions = List("pre"))
    
    lazy val `vnd.lotus-freelance`: MediaType = vndLotusFreelance

    lazy val vndLotusNotes: MediaType =
      MediaType("application", "vnd.lotus-notes", compressible = false, binary = true, fileExtensions = List("nsf"))
    
    lazy val `vnd.lotus-notes`: MediaType = vndLotusNotes

    lazy val vndLotusOrganizer: MediaType =
      MediaType("application", "vnd.lotus-organizer", compressible = false, binary = true, fileExtensions = List("org"))
    
    lazy val `vnd.lotus-organizer`: MediaType = vndLotusOrganizer

    lazy val vndLotusScreencam: MediaType =
      MediaType("application", "vnd.lotus-screencam", compressible = false, binary = true, fileExtensions = List("scm"))
    
    lazy val `vnd.lotus-screencam`: MediaType = vndLotusScreencam

    lazy val vndLotusWordpro: MediaType =
      MediaType("application", "vnd.lotus-wordpro", compressible = false, binary = true, fileExtensions = List("lwp"))
    
    lazy val `vnd.lotus-wordpro`: MediaType = vndLotusWordpro

    lazy val vndMacportsPortpkg: MediaType =
      MediaType("application", "vnd.macports.portpkg", compressible = false, binary = true, fileExtensions = List("portpkg"))
    
    lazy val `vnd.macports.portpkg`: MediaType = vndMacportsPortpkg

    lazy val vndMaml: MediaType =
      MediaType("application", "vnd.maml", compressible = false, binary = true)
    
    lazy val `vnd.maml`: MediaType = vndMaml

    lazy val vndMapboxVectorTile: MediaType =
      MediaType("application", "vnd.mapbox-vector-tile", compressible = false, binary = true, fileExtensions = List("mvt"))
    
    lazy val `vnd.mapbox-vector-tile`: MediaType = vndMapboxVectorTile

    lazy val vndMarlinDrmActiontokenXml: MediaType =
      MediaType("application", "vnd.marlin.drm.actiontoken+xml", compressible = true, binary = true)
    
    lazy val `vnd.marlin.drm.actiontoken+xml`: MediaType = vndMarlinDrmActiontokenXml

    lazy val vndMarlinDrmConftokenXml: MediaType =
      MediaType("application", "vnd.marlin.drm.conftoken+xml", compressible = true, binary = true)
    
    lazy val `vnd.marlin.drm.conftoken+xml`: MediaType = vndMarlinDrmConftokenXml

    lazy val vndMarlinDrmLicenseXml: MediaType =
      MediaType("application", "vnd.marlin.drm.license+xml", compressible = true, binary = true)
    
    lazy val `vnd.marlin.drm.license+xml`: MediaType = vndMarlinDrmLicenseXml

    lazy val vndMarlinDrmMdcf: MediaType =
      MediaType("application", "vnd.marlin.drm.mdcf", compressible = false, binary = true)
    
    lazy val `vnd.marlin.drm.mdcf`: MediaType = vndMarlinDrmMdcf

    lazy val vndMasonJson: MediaType =
      MediaType("application", "vnd.mason+json", compressible = true, binary = false)
    
    lazy val `vnd.mason+json`: MediaType = vndMasonJson

    lazy val vndMaxarArchive3tzZip: MediaType =
      MediaType("application", "vnd.maxar.archive.3tz+zip", compressible = false, binary = true)
    
    lazy val `vnd.maxar.archive.3tz+zip`: MediaType = vndMaxarArchive3tzZip

    lazy val vndMaxmindMaxmindDb: MediaType =
      MediaType("application", "vnd.maxmind.maxmind-db", compressible = false, binary = true)
    
    lazy val `vnd.maxmind.maxmind-db`: MediaType = vndMaxmindMaxmindDb

    lazy val vndMcd: MediaType =
      MediaType("application", "vnd.mcd", compressible = false, binary = true, fileExtensions = List("mcd"))
    
    lazy val `vnd.mcd`: MediaType = vndMcd

    lazy val vndMdl: MediaType =
      MediaType("application", "vnd.mdl", compressible = false, binary = true)
    
    lazy val `vnd.mdl`: MediaType = vndMdl

    lazy val vndMdlMbsdf: MediaType =
      MediaType("application", "vnd.mdl-mbsdf", compressible = false, binary = true)
    
    lazy val `vnd.mdl-mbsdf`: MediaType = vndMdlMbsdf

    lazy val vndMedcalcdata: MediaType =
      MediaType("application", "vnd.medcalcdata", compressible = false, binary = true, fileExtensions = List("mc1"))
    
    lazy val `vnd.medcalcdata`: MediaType = vndMedcalcdata

    lazy val vndMediastationCdkey: MediaType =
      MediaType("application", "vnd.mediastation.cdkey", compressible = false, binary = true, fileExtensions = List("cdkey"))
    
    lazy val `vnd.mediastation.cdkey`: MediaType = vndMediastationCdkey

    lazy val vndMedicalholodeckRecordxr: MediaType =
      MediaType("application", "vnd.medicalholodeck.recordxr", compressible = false, binary = true)
    
    lazy val `vnd.medicalholodeck.recordxr`: MediaType = vndMedicalholodeckRecordxr

    lazy val vndMeridianSlingshot: MediaType =
      MediaType("application", "vnd.meridian-slingshot", compressible = false, binary = true)
    
    lazy val `vnd.meridian-slingshot`: MediaType = vndMeridianSlingshot

    lazy val vndMermaid: MediaType =
      MediaType("application", "vnd.mermaid", compressible = false, binary = true)
    
    lazy val `vnd.mermaid`: MediaType = vndMermaid

    lazy val vndMfer: MediaType =
      MediaType("application", "vnd.mfer", compressible = false, binary = true, fileExtensions = List("mwf"))
    
    lazy val `vnd.mfer`: MediaType = vndMfer

    lazy val vndMfmp: MediaType =
      MediaType("application", "vnd.mfmp", compressible = false, binary = true, fileExtensions = List("mfm"))
    
    lazy val `vnd.mfmp`: MediaType = vndMfmp

    lazy val vndMicroJson: MediaType =
      MediaType("application", "vnd.micro+json", compressible = true, binary = false)
    
    lazy val `vnd.micro+json`: MediaType = vndMicroJson

    lazy val vndMicrografxFlo: MediaType =
      MediaType("application", "vnd.micrografx.flo", compressible = false, binary = true, fileExtensions = List("flo"))
    
    lazy val `vnd.micrografx.flo`: MediaType = vndMicrografxFlo

    lazy val vndMicrografxIgx: MediaType =
      MediaType("application", "vnd.micrografx.igx", compressible = false, binary = true, fileExtensions = List("igx"))
    
    lazy val `vnd.micrografx.igx`: MediaType = vndMicrografxIgx

    lazy val vndMicrosoftPortableExecutable: MediaType =
      MediaType("application", "vnd.microsoft.portable-executable", compressible = false, binary = true)
    
    lazy val `vnd.microsoft.portable-executable`: MediaType = vndMicrosoftPortableExecutable

    lazy val vndMicrosoftWindowsThumbnailCache: MediaType =
      MediaType("application", "vnd.microsoft.windows.thumbnail-cache", compressible = false, binary = true)
    
    lazy val `vnd.microsoft.windows.thumbnail-cache`: MediaType = vndMicrosoftWindowsThumbnailCache

    lazy val vndMieleJson: MediaType =
      MediaType("application", "vnd.miele+json", compressible = true, binary = false)
    
    lazy val `vnd.miele+json`: MediaType = vndMieleJson

    lazy val vndMif: MediaType =
      MediaType("application", "vnd.mif", compressible = false, binary = true, fileExtensions = List("mif"))
    
    lazy val `vnd.mif`: MediaType = vndMif

    lazy val vndMinisoftHp3000Save: MediaType =
      MediaType("application", "vnd.minisoft-hp3000-save", compressible = false, binary = true)
    
    lazy val `vnd.minisoft-hp3000-save`: MediaType = vndMinisoftHp3000Save

    lazy val vndMitsubishiMistyGuardTrustweb: MediaType =
      MediaType("application", "vnd.mitsubishi.misty-guard.trustweb", compressible = false, binary = true)
    
    lazy val `vnd.mitsubishi.misty-guard.trustweb`: MediaType = vndMitsubishiMistyGuardTrustweb

    lazy val vndMobiusDaf: MediaType =
      MediaType("application", "vnd.mobius.daf", compressible = false, binary = true, fileExtensions = List("daf"))
    
    lazy val `vnd.mobius.daf`: MediaType = vndMobiusDaf

    lazy val vndMobiusDis: MediaType =
      MediaType("application", "vnd.mobius.dis", compressible = false, binary = true, fileExtensions = List("dis"))
    
    lazy val `vnd.mobius.dis`: MediaType = vndMobiusDis

    lazy val vndMobiusMbk: MediaType =
      MediaType("application", "vnd.mobius.mbk", compressible = false, binary = true, fileExtensions = List("mbk"))
    
    lazy val `vnd.mobius.mbk`: MediaType = vndMobiusMbk

    lazy val vndMobiusMqy: MediaType =
      MediaType("application", "vnd.mobius.mqy", compressible = false, binary = true, fileExtensions = List("mqy"))
    
    lazy val `vnd.mobius.mqy`: MediaType = vndMobiusMqy

    lazy val vndMobiusMsl: MediaType =
      MediaType("application", "vnd.mobius.msl", compressible = false, binary = true, fileExtensions = List("msl"))
    
    lazy val `vnd.mobius.msl`: MediaType = vndMobiusMsl

    lazy val vndMobiusPlc: MediaType =
      MediaType("application", "vnd.mobius.plc", compressible = false, binary = true, fileExtensions = List("plc"))
    
    lazy val `vnd.mobius.plc`: MediaType = vndMobiusPlc

    lazy val vndMobiusTxf: MediaType =
      MediaType("application", "vnd.mobius.txf", compressible = false, binary = true, fileExtensions = List("txf"))
    
    lazy val `vnd.mobius.txf`: MediaType = vndMobiusTxf

    lazy val vndModl: MediaType =
      MediaType("application", "vnd.modl", compressible = false, binary = true)
    
    lazy val `vnd.modl`: MediaType = vndModl

    lazy val vndMophunApplication: MediaType =
      MediaType("application", "vnd.mophun.application", compressible = false, binary = true, fileExtensions = List("mpn"))
    
    lazy val `vnd.mophun.application`: MediaType = vndMophunApplication

    lazy val vndMophunCertificate: MediaType =
      MediaType("application", "vnd.mophun.certificate", compressible = false, binary = true, fileExtensions = List("mpc"))
    
    lazy val `vnd.mophun.certificate`: MediaType = vndMophunCertificate

    lazy val vndMotorolaFlexsuite: MediaType =
      MediaType("application", "vnd.motorola.flexsuite", compressible = false, binary = true)
    
    lazy val `vnd.motorola.flexsuite`: MediaType = vndMotorolaFlexsuite

    lazy val vndMotorolaFlexsuiteAdsi: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.adsi", compressible = false, binary = true)
    
    lazy val `vnd.motorola.flexsuite.adsi`: MediaType = vndMotorolaFlexsuiteAdsi

    lazy val vndMotorolaFlexsuiteFis: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.fis", compressible = false, binary = true)
    
    lazy val `vnd.motorola.flexsuite.fis`: MediaType = vndMotorolaFlexsuiteFis

    lazy val vndMotorolaFlexsuiteGotap: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.gotap", compressible = false, binary = true)
    
    lazy val `vnd.motorola.flexsuite.gotap`: MediaType = vndMotorolaFlexsuiteGotap

    lazy val vndMotorolaFlexsuiteKmr: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.kmr", compressible = false, binary = true)
    
    lazy val `vnd.motorola.flexsuite.kmr`: MediaType = vndMotorolaFlexsuiteKmr

    lazy val vndMotorolaFlexsuiteTtc: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.ttc", compressible = false, binary = true)
    
    lazy val `vnd.motorola.flexsuite.ttc`: MediaType = vndMotorolaFlexsuiteTtc

    lazy val vndMotorolaFlexsuiteWem: MediaType =
      MediaType("application", "vnd.motorola.flexsuite.wem", compressible = false, binary = true)
    
    lazy val `vnd.motorola.flexsuite.wem`: MediaType = vndMotorolaFlexsuiteWem

    lazy val vndMotorolaIprm: MediaType =
      MediaType("application", "vnd.motorola.iprm", compressible = false, binary = true)
    
    lazy val `vnd.motorola.iprm`: MediaType = vndMotorolaIprm

    lazy val vndMozillaXulXml: MediaType =
      MediaType("application", "vnd.mozilla.xul+xml", compressible = true, binary = true, fileExtensions = List("xul"))
    
    lazy val `vnd.mozilla.xul+xml`: MediaType = vndMozillaXulXml

    lazy val vndMs3mfdocument: MediaType =
      MediaType("application", "vnd.ms-3mfdocument", compressible = false, binary = true)
    
    lazy val `vnd.ms-3mfdocument`: MediaType = vndMs3mfdocument

    lazy val vndMsArtgalry: MediaType =
      MediaType("application", "vnd.ms-artgalry", compressible = false, binary = true, fileExtensions = List("cil"))
    
    lazy val `vnd.ms-artgalry`: MediaType = vndMsArtgalry

    lazy val vndMsAsf: MediaType =
      MediaType("application", "vnd.ms-asf", compressible = false, binary = true)
    
    lazy val `vnd.ms-asf`: MediaType = vndMsAsf

    lazy val vndMsCabCompressed: MediaType =
      MediaType("application", "vnd.ms-cab-compressed", compressible = false, binary = true, fileExtensions = List("cab"))
    
    lazy val `vnd.ms-cab-compressed`: MediaType = vndMsCabCompressed

    lazy val vndMsColorIccprofile: MediaType =
      MediaType("application", "vnd.ms-color.iccprofile", compressible = false, binary = true)
    
    lazy val `vnd.ms-color.iccprofile`: MediaType = vndMsColorIccprofile

    lazy val vndMsExcel: MediaType =
      MediaType("application", "vnd.ms-excel", compressible = false, binary = true, fileExtensions = List("xls", "xlm", "xla", "xlc", "xlt", "xlw"))
    
    lazy val `vnd.ms-excel`: MediaType = vndMsExcel

    lazy val vndMsExcelAddinMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-excel.addin.macroenabled.12", compressible = false, binary = true, fileExtensions = List("xlam"))
    
    lazy val `vnd.ms-excel.addin.macroenabled.12`: MediaType = vndMsExcelAddinMacroenabled12

    lazy val vndMsExcelSheetBinaryMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-excel.sheet.binary.macroenabled.12", compressible = false, binary = true, fileExtensions = List("xlsb"))
    
    lazy val `vnd.ms-excel.sheet.binary.macroenabled.12`: MediaType = vndMsExcelSheetBinaryMacroenabled12

    lazy val vndMsExcelSheetMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-excel.sheet.macroenabled.12", compressible = false, binary = true, fileExtensions = List("xlsm"))
    
    lazy val `vnd.ms-excel.sheet.macroenabled.12`: MediaType = vndMsExcelSheetMacroenabled12

    lazy val vndMsExcelTemplateMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-excel.template.macroenabled.12", compressible = false, binary = true, fileExtensions = List("xltm"))
    
    lazy val `vnd.ms-excel.template.macroenabled.12`: MediaType = vndMsExcelTemplateMacroenabled12

    lazy val vndMsFontobject: MediaType =
      MediaType("application", "vnd.ms-fontobject", compressible = true, binary = true, fileExtensions = List("eot"))
    
    lazy val `vnd.ms-fontobject`: MediaType = vndMsFontobject

    lazy val vndMsHtmlhelp: MediaType =
      MediaType("application", "vnd.ms-htmlhelp", compressible = false, binary = true, fileExtensions = List("chm"))
    
    lazy val `vnd.ms-htmlhelp`: MediaType = vndMsHtmlhelp

    lazy val vndMsIms: MediaType =
      MediaType("application", "vnd.ms-ims", compressible = false, binary = true, fileExtensions = List("ims"))
    
    lazy val `vnd.ms-ims`: MediaType = vndMsIms

    lazy val vndMsLrm: MediaType =
      MediaType("application", "vnd.ms-lrm", compressible = false, binary = true, fileExtensions = List("lrm"))
    
    lazy val `vnd.ms-lrm`: MediaType = vndMsLrm

    lazy val vndMsOfficeActivexXml: MediaType =
      MediaType("application", "vnd.ms-office.activex+xml", compressible = true, binary = true)
    
    lazy val `vnd.ms-office.activex+xml`: MediaType = vndMsOfficeActivexXml

    lazy val vndMsOfficetheme: MediaType =
      MediaType("application", "vnd.ms-officetheme", compressible = false, binary = true, fileExtensions = List("thmx"))
    
    lazy val `vnd.ms-officetheme`: MediaType = vndMsOfficetheme

    lazy val vndMsOpentype: MediaType =
      MediaType("application", "vnd.ms-opentype", compressible = true, binary = true)
    
    lazy val `vnd.ms-opentype`: MediaType = vndMsOpentype

    lazy val vndMsOutlook: MediaType =
      MediaType("application", "vnd.ms-outlook", compressible = false, binary = true, fileExtensions = List("msg"))
    
    lazy val `vnd.ms-outlook`: MediaType = vndMsOutlook

    lazy val vndMsPackageObfuscatedOpentype: MediaType =
      MediaType("application", "vnd.ms-package.obfuscated-opentype", compressible = false, binary = true)
    
    lazy val `vnd.ms-package.obfuscated-opentype`: MediaType = vndMsPackageObfuscatedOpentype

    lazy val vndMsPkiSeccat: MediaType =
      MediaType("application", "vnd.ms-pki.seccat", compressible = false, binary = true, fileExtensions = List("cat"))
    
    lazy val `vnd.ms-pki.seccat`: MediaType = vndMsPkiSeccat

    lazy val vndMsPkiStl: MediaType =
      MediaType("application", "vnd.ms-pki.stl", compressible = false, binary = true, fileExtensions = List("stl"))
    
    lazy val `vnd.ms-pki.stl`: MediaType = vndMsPkiStl

    lazy val vndMsPlayreadyInitiatorXml: MediaType =
      MediaType("application", "vnd.ms-playready.initiator+xml", compressible = true, binary = true)
    
    lazy val `vnd.ms-playready.initiator+xml`: MediaType = vndMsPlayreadyInitiatorXml

    lazy val vndMsPowerpoint: MediaType =
      MediaType("application", "vnd.ms-powerpoint", compressible = false, binary = true, fileExtensions = List("ppt", "pps", "pot"))
    
    lazy val `vnd.ms-powerpoint`: MediaType = vndMsPowerpoint

    lazy val vndMsPowerpointAddinMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-powerpoint.addin.macroenabled.12", compressible = false, binary = true, fileExtensions = List("ppam"))
    
    lazy val `vnd.ms-powerpoint.addin.macroenabled.12`: MediaType = vndMsPowerpointAddinMacroenabled12

    lazy val vndMsPowerpointPresentationMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-powerpoint.presentation.macroenabled.12", compressible = false, binary = true, fileExtensions = List("pptm"))
    
    lazy val `vnd.ms-powerpoint.presentation.macroenabled.12`: MediaType = vndMsPowerpointPresentationMacroenabled12

    lazy val vndMsPowerpointSlideMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-powerpoint.slide.macroenabled.12", compressible = false, binary = true, fileExtensions = List("sldm"))
    
    lazy val `vnd.ms-powerpoint.slide.macroenabled.12`: MediaType = vndMsPowerpointSlideMacroenabled12

    lazy val vndMsPowerpointSlideshowMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-powerpoint.slideshow.macroenabled.12", compressible = false, binary = true, fileExtensions = List("ppsm"))
    
    lazy val `vnd.ms-powerpoint.slideshow.macroenabled.12`: MediaType = vndMsPowerpointSlideshowMacroenabled12

    lazy val vndMsPowerpointTemplateMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-powerpoint.template.macroenabled.12", compressible = false, binary = true, fileExtensions = List("potm"))
    
    lazy val `vnd.ms-powerpoint.template.macroenabled.12`: MediaType = vndMsPowerpointTemplateMacroenabled12

    lazy val vndMsPrintdevicecapabilitiesXml: MediaType =
      MediaType("application", "vnd.ms-printdevicecapabilities+xml", compressible = true, binary = true)
    
    lazy val `vnd.ms-printdevicecapabilities+xml`: MediaType = vndMsPrintdevicecapabilitiesXml

    lazy val vndMsPrintingPrintticketXml: MediaType =
      MediaType("application", "vnd.ms-printing.printticket+xml", compressible = true, binary = true)
    
    lazy val `vnd.ms-printing.printticket+xml`: MediaType = vndMsPrintingPrintticketXml

    lazy val vndMsPrintschematicketXml: MediaType =
      MediaType("application", "vnd.ms-printschematicket+xml", compressible = true, binary = true)
    
    lazy val `vnd.ms-printschematicket+xml`: MediaType = vndMsPrintschematicketXml

    lazy val vndMsProject: MediaType =
      MediaType("application", "vnd.ms-project", compressible = false, binary = true, fileExtensions = List("mpp", "mpt"))
    
    lazy val `vnd.ms-project`: MediaType = vndMsProject

    lazy val vndMsTnef: MediaType =
      MediaType("application", "vnd.ms-tnef", compressible = false, binary = true)
    
    lazy val `vnd.ms-tnef`: MediaType = vndMsTnef

    lazy val vndMsVisioViewer: MediaType =
      MediaType("application", "vnd.ms-visio.viewer", compressible = false, binary = true, fileExtensions = List("vdx"))
    
    lazy val `vnd.ms-visio.viewer`: MediaType = vndMsVisioViewer

    lazy val vndMsWindowsDevicepairing: MediaType =
      MediaType("application", "vnd.ms-windows.devicepairing", compressible = false, binary = true)
    
    lazy val `vnd.ms-windows.devicepairing`: MediaType = vndMsWindowsDevicepairing

    lazy val vndMsWindowsNwprintingOob: MediaType =
      MediaType("application", "vnd.ms-windows.nwprinting.oob", compressible = false, binary = true)
    
    lazy val `vnd.ms-windows.nwprinting.oob`: MediaType = vndMsWindowsNwprintingOob

    lazy val vndMsWindowsPrinterpairing: MediaType =
      MediaType("application", "vnd.ms-windows.printerpairing", compressible = false, binary = true)
    
    lazy val `vnd.ms-windows.printerpairing`: MediaType = vndMsWindowsPrinterpairing

    lazy val vndMsWindowsWsdOob: MediaType =
      MediaType("application", "vnd.ms-windows.wsd.oob", compressible = false, binary = true)
    
    lazy val `vnd.ms-windows.wsd.oob`: MediaType = vndMsWindowsWsdOob

    lazy val vndMsWmdrmLicChlgReq: MediaType =
      MediaType("application", "vnd.ms-wmdrm.lic-chlg-req", compressible = false, binary = true)
    
    lazy val `vnd.ms-wmdrm.lic-chlg-req`: MediaType = vndMsWmdrmLicChlgReq

    lazy val vndMsWmdrmLicResp: MediaType =
      MediaType("application", "vnd.ms-wmdrm.lic-resp", compressible = false, binary = true)
    
    lazy val `vnd.ms-wmdrm.lic-resp`: MediaType = vndMsWmdrmLicResp

    lazy val vndMsWmdrmMeterChlgReq: MediaType =
      MediaType("application", "vnd.ms-wmdrm.meter-chlg-req", compressible = false, binary = true)
    
    lazy val `vnd.ms-wmdrm.meter-chlg-req`: MediaType = vndMsWmdrmMeterChlgReq

    lazy val vndMsWmdrmMeterResp: MediaType =
      MediaType("application", "vnd.ms-wmdrm.meter-resp", compressible = false, binary = true)
    
    lazy val `vnd.ms-wmdrm.meter-resp`: MediaType = vndMsWmdrmMeterResp

    lazy val vndMsWordDocumentMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-word.document.macroenabled.12", compressible = false, binary = true, fileExtensions = List("docm"))
    
    lazy val `vnd.ms-word.document.macroenabled.12`: MediaType = vndMsWordDocumentMacroenabled12

    lazy val vndMsWordTemplateMacroenabled12: MediaType =
      MediaType("application", "vnd.ms-word.template.macroenabled.12", compressible = false, binary = true, fileExtensions = List("dotm"))
    
    lazy val `vnd.ms-word.template.macroenabled.12`: MediaType = vndMsWordTemplateMacroenabled12

    lazy val vndMsWorks: MediaType =
      MediaType("application", "vnd.ms-works", compressible = false, binary = true, fileExtensions = List("wps", "wks", "wcm", "wdb"))
    
    lazy val `vnd.ms-works`: MediaType = vndMsWorks

    lazy val vndMsWpl: MediaType =
      MediaType("application", "vnd.ms-wpl", compressible = false, binary = true, fileExtensions = List("wpl"))
    
    lazy val `vnd.ms-wpl`: MediaType = vndMsWpl

    lazy val vndMsXpsdocument: MediaType =
      MediaType("application", "vnd.ms-xpsdocument", compressible = false, binary = true, fileExtensions = List("xps"))
    
    lazy val `vnd.ms-xpsdocument`: MediaType = vndMsXpsdocument

    lazy val vndMsaDiskImage: MediaType =
      MediaType("application", "vnd.msa-disk-image", compressible = false, binary = true)
    
    lazy val `vnd.msa-disk-image`: MediaType = vndMsaDiskImage

    lazy val vndMseq: MediaType =
      MediaType("application", "vnd.mseq", compressible = false, binary = true, fileExtensions = List("mseq"))
    
    lazy val `vnd.mseq`: MediaType = vndMseq

    lazy val vndMsgpack: MediaType =
      MediaType("application", "vnd.msgpack", compressible = false, binary = true)
    
    lazy val `vnd.msgpack`: MediaType = vndMsgpack

    lazy val vndMsign: MediaType =
      MediaType("application", "vnd.msign", compressible = false, binary = true)
    
    lazy val `vnd.msign`: MediaType = vndMsign

    lazy val vndMultiadCreator: MediaType =
      MediaType("application", "vnd.multiad.creator", compressible = false, binary = true)
    
    lazy val `vnd.multiad.creator`: MediaType = vndMultiadCreator

    lazy val vndMultiadCreatorCif: MediaType =
      MediaType("application", "vnd.multiad.creator.cif", compressible = false, binary = true)
    
    lazy val `vnd.multiad.creator.cif`: MediaType = vndMultiadCreatorCif

    lazy val vndMusicNiff: MediaType =
      MediaType("application", "vnd.music-niff", compressible = false, binary = true)
    
    lazy val `vnd.music-niff`: MediaType = vndMusicNiff

    lazy val vndMusician: MediaType =
      MediaType("application", "vnd.musician", compressible = false, binary = true, fileExtensions = List("mus"))
    
    lazy val `vnd.musician`: MediaType = vndMusician

    lazy val vndMuveeStyle: MediaType =
      MediaType("application", "vnd.muvee.style", compressible = false, binary = true, fileExtensions = List("msty"))
    
    lazy val `vnd.muvee.style`: MediaType = vndMuveeStyle

    lazy val vndMynfc: MediaType =
      MediaType("application", "vnd.mynfc", compressible = false, binary = true, fileExtensions = List("taglet"))
    
    lazy val `vnd.mynfc`: MediaType = vndMynfc

    lazy val vndNacamarYbridJson: MediaType =
      MediaType("application", "vnd.nacamar.ybrid+json", compressible = true, binary = false)
    
    lazy val `vnd.nacamar.ybrid+json`: MediaType = vndNacamarYbridJson

    lazy val vndNatoBindingdataobjectCbor: MediaType =
      MediaType("application", "vnd.nato.bindingdataobject+cbor", compressible = false, binary = true)
    
    lazy val `vnd.nato.bindingdataobject+cbor`: MediaType = vndNatoBindingdataobjectCbor

    lazy val vndNatoBindingdataobjectJson: MediaType =
      MediaType("application", "vnd.nato.bindingdataobject+json", compressible = true, binary = false)
    
    lazy val `vnd.nato.bindingdataobject+json`: MediaType = vndNatoBindingdataobjectJson

    lazy val vndNatoBindingdataobjectXml: MediaType =
      MediaType("application", "vnd.nato.bindingdataobject+xml", compressible = true, binary = true, fileExtensions = List("bdo"))
    
    lazy val `vnd.nato.bindingdataobject+xml`: MediaType = vndNatoBindingdataobjectXml

    lazy val vndNatoOpenxmlformatsPackageIepdZip: MediaType =
      MediaType("application", "vnd.nato.openxmlformats-package.iepd+zip", compressible = false, binary = true)
    
    lazy val `vnd.nato.openxmlformats-package.iepd+zip`: MediaType = vndNatoOpenxmlformatsPackageIepdZip

    lazy val vndNcdControl: MediaType =
      MediaType("application", "vnd.ncd.control", compressible = false, binary = true)
    
    lazy val `vnd.ncd.control`: MediaType = vndNcdControl

    lazy val vndNcdReference: MediaType =
      MediaType("application", "vnd.ncd.reference", compressible = false, binary = true)
    
    lazy val `vnd.ncd.reference`: MediaType = vndNcdReference

    lazy val vndNearstInvJson: MediaType =
      MediaType("application", "vnd.nearst.inv+json", compressible = true, binary = false)
    
    lazy val `vnd.nearst.inv+json`: MediaType = vndNearstInvJson

    lazy val vndNebumindLine: MediaType =
      MediaType("application", "vnd.nebumind.line", compressible = false, binary = true)
    
    lazy val `vnd.nebumind.line`: MediaType = vndNebumindLine

    lazy val vndNervana: MediaType =
      MediaType("application", "vnd.nervana", compressible = false, binary = true)
    
    lazy val `vnd.nervana`: MediaType = vndNervana

    lazy val vndNetfpx: MediaType =
      MediaType("application", "vnd.netfpx", compressible = false, binary = true)
    
    lazy val `vnd.netfpx`: MediaType = vndNetfpx

    lazy val vndNeurolanguageNlu: MediaType =
      MediaType("application", "vnd.neurolanguage.nlu", compressible = false, binary = true, fileExtensions = List("nlu"))
    
    lazy val `vnd.neurolanguage.nlu`: MediaType = vndNeurolanguageNlu

    lazy val vndNimn: MediaType =
      MediaType("application", "vnd.nimn", compressible = false, binary = true)
    
    lazy val `vnd.nimn`: MediaType = vndNimn

    lazy val vndNintendoNitroRom: MediaType =
      MediaType("application", "vnd.nintendo.nitro.rom", compressible = false, binary = true)
    
    lazy val `vnd.nintendo.nitro.rom`: MediaType = vndNintendoNitroRom

    lazy val vndNintendoSnesRom: MediaType =
      MediaType("application", "vnd.nintendo.snes.rom", compressible = false, binary = true)
    
    lazy val `vnd.nintendo.snes.rom`: MediaType = vndNintendoSnesRom

    lazy val vndNitf: MediaType =
      MediaType("application", "vnd.nitf", compressible = false, binary = true, fileExtensions = List("ntf", "nitf"))
    
    lazy val `vnd.nitf`: MediaType = vndNitf

    lazy val vndNoblenetDirectory: MediaType =
      MediaType("application", "vnd.noblenet-directory", compressible = false, binary = true, fileExtensions = List("nnd"))
    
    lazy val `vnd.noblenet-directory`: MediaType = vndNoblenetDirectory

    lazy val vndNoblenetSealer: MediaType =
      MediaType("application", "vnd.noblenet-sealer", compressible = false, binary = true, fileExtensions = List("nns"))
    
    lazy val `vnd.noblenet-sealer`: MediaType = vndNoblenetSealer

    lazy val vndNoblenetWeb: MediaType =
      MediaType("application", "vnd.noblenet-web", compressible = false, binary = true, fileExtensions = List("nnw"))
    
    lazy val `vnd.noblenet-web`: MediaType = vndNoblenetWeb

    lazy val vndNokiaCatalogs: MediaType =
      MediaType("application", "vnd.nokia.catalogs", compressible = false, binary = true)
    
    lazy val `vnd.nokia.catalogs`: MediaType = vndNokiaCatalogs

    lazy val vndNokiaConmlWbxml: MediaType =
      MediaType("application", "vnd.nokia.conml+wbxml", compressible = false, binary = true)
    
    lazy val `vnd.nokia.conml+wbxml`: MediaType = vndNokiaConmlWbxml

    lazy val vndNokiaConmlXml: MediaType =
      MediaType("application", "vnd.nokia.conml+xml", compressible = true, binary = true)
    
    lazy val `vnd.nokia.conml+xml`: MediaType = vndNokiaConmlXml

    lazy val vndNokiaIptvConfigXml: MediaType =
      MediaType("application", "vnd.nokia.iptv.config+xml", compressible = true, binary = true)
    
    lazy val `vnd.nokia.iptv.config+xml`: MediaType = vndNokiaIptvConfigXml

    lazy val vndNokiaIsdsRadioPresets: MediaType =
      MediaType("application", "vnd.nokia.isds-radio-presets", compressible = false, binary = true)
    
    lazy val `vnd.nokia.isds-radio-presets`: MediaType = vndNokiaIsdsRadioPresets

    lazy val vndNokiaLandmarkWbxml: MediaType =
      MediaType("application", "vnd.nokia.landmark+wbxml", compressible = false, binary = true)
    
    lazy val `vnd.nokia.landmark+wbxml`: MediaType = vndNokiaLandmarkWbxml

    lazy val vndNokiaLandmarkXml: MediaType =
      MediaType("application", "vnd.nokia.landmark+xml", compressible = true, binary = true)
    
    lazy val `vnd.nokia.landmark+xml`: MediaType = vndNokiaLandmarkXml

    lazy val vndNokiaLandmarkcollectionXml: MediaType =
      MediaType("application", "vnd.nokia.landmarkcollection+xml", compressible = true, binary = true)
    
    lazy val `vnd.nokia.landmarkcollection+xml`: MediaType = vndNokiaLandmarkcollectionXml

    lazy val vndNokiaNGageAcXml: MediaType =
      MediaType("application", "vnd.nokia.n-gage.ac+xml", compressible = true, binary = true, fileExtensions = List("ac"))
    
    lazy val `vnd.nokia.n-gage.ac+xml`: MediaType = vndNokiaNGageAcXml

    lazy val vndNokiaNGageData: MediaType =
      MediaType("application", "vnd.nokia.n-gage.data", compressible = false, binary = true, fileExtensions = List("ngdat"))
    
    lazy val `vnd.nokia.n-gage.data`: MediaType = vndNokiaNGageData

    lazy val vndNokiaNGageSymbianInstall: MediaType =
      MediaType("application", "vnd.nokia.n-gage.symbian.install", compressible = false, binary = true, fileExtensions = List("n-gage"))
    
    lazy val `vnd.nokia.n-gage.symbian.install`: MediaType = vndNokiaNGageSymbianInstall

    lazy val vndNokiaNcd: MediaType =
      MediaType("application", "vnd.nokia.ncd", compressible = false, binary = true)
    
    lazy val `vnd.nokia.ncd`: MediaType = vndNokiaNcd

    lazy val vndNokiaPcdWbxml: MediaType =
      MediaType("application", "vnd.nokia.pcd+wbxml", compressible = false, binary = true)
    
    lazy val `vnd.nokia.pcd+wbxml`: MediaType = vndNokiaPcdWbxml

    lazy val vndNokiaPcdXml: MediaType =
      MediaType("application", "vnd.nokia.pcd+xml", compressible = true, binary = true)
    
    lazy val `vnd.nokia.pcd+xml`: MediaType = vndNokiaPcdXml

    lazy val vndNokiaRadioPreset: MediaType =
      MediaType("application", "vnd.nokia.radio-preset", compressible = false, binary = true, fileExtensions = List("rpst"))
    
    lazy val `vnd.nokia.radio-preset`: MediaType = vndNokiaRadioPreset

    lazy val vndNokiaRadioPresets: MediaType =
      MediaType("application", "vnd.nokia.radio-presets", compressible = false, binary = true, fileExtensions = List("rpss"))
    
    lazy val `vnd.nokia.radio-presets`: MediaType = vndNokiaRadioPresets

    lazy val vndNovadigmEdm: MediaType =
      MediaType("application", "vnd.novadigm.edm", compressible = false, binary = true, fileExtensions = List("edm"))
    
    lazy val `vnd.novadigm.edm`: MediaType = vndNovadigmEdm

    lazy val vndNovadigmEdx: MediaType =
      MediaType("application", "vnd.novadigm.edx", compressible = false, binary = true, fileExtensions = List("edx"))
    
    lazy val `vnd.novadigm.edx`: MediaType = vndNovadigmEdx

    lazy val vndNovadigmExt: MediaType =
      MediaType("application", "vnd.novadigm.ext", compressible = false, binary = true, fileExtensions = List("ext"))
    
    lazy val `vnd.novadigm.ext`: MediaType = vndNovadigmExt

    lazy val vndNttLocalContentShare: MediaType =
      MediaType("application", "vnd.ntt-local.content-share", compressible = false, binary = true)
    
    lazy val `vnd.ntt-local.content-share`: MediaType = vndNttLocalContentShare

    lazy val vndNttLocalFileTransfer: MediaType =
      MediaType("application", "vnd.ntt-local.file-transfer", compressible = false, binary = true)
    
    lazy val `vnd.ntt-local.file-transfer`: MediaType = vndNttLocalFileTransfer

    lazy val vndNttLocalOgwRemoteAccess: MediaType =
      MediaType("application", "vnd.ntt-local.ogw_remote-access", compressible = false, binary = true)
    
    lazy val `vnd.ntt-local.ogw_remote-access`: MediaType = vndNttLocalOgwRemoteAccess

    lazy val vndNttLocalSipTaRemote: MediaType =
      MediaType("application", "vnd.ntt-local.sip-ta_remote", compressible = false, binary = true)
    
    lazy val `vnd.ntt-local.sip-ta_remote`: MediaType = vndNttLocalSipTaRemote

    lazy val vndNttLocalSipTaTcpStream: MediaType =
      MediaType("application", "vnd.ntt-local.sip-ta_tcp_stream", compressible = false, binary = true)
    
    lazy val `vnd.ntt-local.sip-ta_tcp_stream`: MediaType = vndNttLocalSipTaTcpStream

    lazy val vndNubaltecNudokuGame: MediaType =
      MediaType("application", "vnd.nubaltec.nudoku-game", compressible = false, binary = true)
    
    lazy val `vnd.nubaltec.nudoku-game`: MediaType = vndNubaltecNudokuGame

    lazy val vndOaiWorkflows: MediaType =
      MediaType("application", "vnd.oai.workflows", compressible = false, binary = true)
    
    lazy val `vnd.oai.workflows`: MediaType = vndOaiWorkflows

    lazy val vndOaiWorkflowsJson: MediaType =
      MediaType("application", "vnd.oai.workflows+json", compressible = true, binary = false)
    
    lazy val `vnd.oai.workflows+json`: MediaType = vndOaiWorkflowsJson

    lazy val vndOaiWorkflowsYaml: MediaType =
      MediaType("application", "vnd.oai.workflows+yaml", compressible = false, binary = true)
    
    lazy val `vnd.oai.workflows+yaml`: MediaType = vndOaiWorkflowsYaml

    lazy val vndOasisOpendocumentBase: MediaType =
      MediaType("application", "vnd.oasis.opendocument.base", compressible = false, binary = true)
    
    lazy val `vnd.oasis.opendocument.base`: MediaType = vndOasisOpendocumentBase

    lazy val vndOasisOpendocumentChart: MediaType =
      MediaType("application", "vnd.oasis.opendocument.chart", compressible = false, binary = true, fileExtensions = List("odc"))
    
    lazy val `vnd.oasis.opendocument.chart`: MediaType = vndOasisOpendocumentChart

    lazy val vndOasisOpendocumentChartTemplate: MediaType =
      MediaType("application", "vnd.oasis.opendocument.chart-template", compressible = false, binary = true, fileExtensions = List("otc"))
    
    lazy val `vnd.oasis.opendocument.chart-template`: MediaType = vndOasisOpendocumentChartTemplate

    lazy val vndOasisOpendocumentDatabase: MediaType =
      MediaType("application", "vnd.oasis.opendocument.database", compressible = false, binary = true, fileExtensions = List("odb"))
    
    lazy val `vnd.oasis.opendocument.database`: MediaType = vndOasisOpendocumentDatabase

    lazy val vndOasisOpendocumentFormula: MediaType =
      MediaType("application", "vnd.oasis.opendocument.formula", compressible = false, binary = true, fileExtensions = List("odf"))
    
    lazy val `vnd.oasis.opendocument.formula`: MediaType = vndOasisOpendocumentFormula

    lazy val vndOasisOpendocumentFormulaTemplate: MediaType =
      MediaType("application", "vnd.oasis.opendocument.formula-template", compressible = false, binary = true, fileExtensions = List("odft"))
    
    lazy val `vnd.oasis.opendocument.formula-template`: MediaType = vndOasisOpendocumentFormulaTemplate

    lazy val vndOasisOpendocumentGraphics: MediaType =
      MediaType("application", "vnd.oasis.opendocument.graphics", compressible = false, binary = true, fileExtensions = List("odg"))
    
    lazy val `vnd.oasis.opendocument.graphics`: MediaType = vndOasisOpendocumentGraphics

    lazy val vndOasisOpendocumentGraphicsTemplate: MediaType =
      MediaType("application", "vnd.oasis.opendocument.graphics-template", compressible = false, binary = true, fileExtensions = List("otg"))
    
    lazy val `vnd.oasis.opendocument.graphics-template`: MediaType = vndOasisOpendocumentGraphicsTemplate

    lazy val vndOasisOpendocumentImage: MediaType =
      MediaType("application", "vnd.oasis.opendocument.image", compressible = false, binary = true, fileExtensions = List("odi"))
    
    lazy val `vnd.oasis.opendocument.image`: MediaType = vndOasisOpendocumentImage

    lazy val vndOasisOpendocumentImageTemplate: MediaType =
      MediaType("application", "vnd.oasis.opendocument.image-template", compressible = false, binary = true, fileExtensions = List("oti"))
    
    lazy val `vnd.oasis.opendocument.image-template`: MediaType = vndOasisOpendocumentImageTemplate

    lazy val vndOasisOpendocumentPresentation: MediaType =
      MediaType("application", "vnd.oasis.opendocument.presentation", compressible = false, binary = true, fileExtensions = List("odp"))
    
    lazy val `vnd.oasis.opendocument.presentation`: MediaType = vndOasisOpendocumentPresentation

    lazy val vndOasisOpendocumentPresentationTemplate: MediaType =
      MediaType("application", "vnd.oasis.opendocument.presentation-template", compressible = false, binary = true, fileExtensions = List("otp"))
    
    lazy val `vnd.oasis.opendocument.presentation-template`: MediaType = vndOasisOpendocumentPresentationTemplate

    lazy val vndOasisOpendocumentSpreadsheet: MediaType =
      MediaType("application", "vnd.oasis.opendocument.spreadsheet", compressible = false, binary = true, fileExtensions = List("ods"))
    
    lazy val `vnd.oasis.opendocument.spreadsheet`: MediaType = vndOasisOpendocumentSpreadsheet

    lazy val vndOasisOpendocumentSpreadsheetTemplate: MediaType =
      MediaType("application", "vnd.oasis.opendocument.spreadsheet-template", compressible = false, binary = true, fileExtensions = List("ots"))
    
    lazy val `vnd.oasis.opendocument.spreadsheet-template`: MediaType = vndOasisOpendocumentSpreadsheetTemplate

    lazy val vndOasisOpendocumentText: MediaType =
      MediaType("application", "vnd.oasis.opendocument.text", compressible = false, binary = true, fileExtensions = List("odt"))
    
    lazy val `vnd.oasis.opendocument.text`: MediaType = vndOasisOpendocumentText

    lazy val vndOasisOpendocumentTextMaster: MediaType =
      MediaType("application", "vnd.oasis.opendocument.text-master", compressible = false, binary = true, fileExtensions = List("odm"))
    
    lazy val `vnd.oasis.opendocument.text-master`: MediaType = vndOasisOpendocumentTextMaster

    lazy val vndOasisOpendocumentTextMasterTemplate: MediaType =
      MediaType("application", "vnd.oasis.opendocument.text-master-template", compressible = false, binary = true)
    
    lazy val `vnd.oasis.opendocument.text-master-template`: MediaType = vndOasisOpendocumentTextMasterTemplate

    lazy val vndOasisOpendocumentTextTemplate: MediaType =
      MediaType("application", "vnd.oasis.opendocument.text-template", compressible = false, binary = true, fileExtensions = List("ott"))
    
    lazy val `vnd.oasis.opendocument.text-template`: MediaType = vndOasisOpendocumentTextTemplate

    lazy val vndOasisOpendocumentTextWeb: MediaType =
      MediaType("application", "vnd.oasis.opendocument.text-web", compressible = false, binary = true, fileExtensions = List("oth"))
    
    lazy val `vnd.oasis.opendocument.text-web`: MediaType = vndOasisOpendocumentTextWeb

    lazy val vndObn: MediaType =
      MediaType("application", "vnd.obn", compressible = false, binary = true)
    
    lazy val `vnd.obn`: MediaType = vndObn

    lazy val vndOcfCbor: MediaType =
      MediaType("application", "vnd.ocf+cbor", compressible = false, binary = true)
    
    lazy val `vnd.ocf+cbor`: MediaType = vndOcfCbor

    lazy val vndOciImageManifestV1Json: MediaType =
      MediaType("application", "vnd.oci.image.manifest.v1+json", compressible = true, binary = false)
    
    lazy val `vnd.oci.image.manifest.v1+json`: MediaType = vndOciImageManifestV1Json

    lazy val vndOftnL10nJson: MediaType =
      MediaType("application", "vnd.oftn.l10n+json", compressible = true, binary = false)
    
    lazy val `vnd.oftn.l10n+json`: MediaType = vndOftnL10nJson

    lazy val vndOipfContentaccessdownloadXml: MediaType =
      MediaType("application", "vnd.oipf.contentaccessdownload+xml", compressible = true, binary = true)
    
    lazy val `vnd.oipf.contentaccessdownload+xml`: MediaType = vndOipfContentaccessdownloadXml

    lazy val vndOipfContentaccessstreamingXml: MediaType =
      MediaType("application", "vnd.oipf.contentaccessstreaming+xml", compressible = true, binary = true)
    
    lazy val `vnd.oipf.contentaccessstreaming+xml`: MediaType = vndOipfContentaccessstreamingXml

    lazy val vndOipfCspgHexbinary: MediaType =
      MediaType("application", "vnd.oipf.cspg-hexbinary", compressible = false, binary = true)
    
    lazy val `vnd.oipf.cspg-hexbinary`: MediaType = vndOipfCspgHexbinary

    lazy val vndOipfDaeSvgXml: MediaType =
      MediaType("application", "vnd.oipf.dae.svg+xml", compressible = true, binary = true)
    
    lazy val `vnd.oipf.dae.svg+xml`: MediaType = vndOipfDaeSvgXml

    lazy val vndOipfDaeXhtmlXml: MediaType =
      MediaType("application", "vnd.oipf.dae.xhtml+xml", compressible = true, binary = true)
    
    lazy val `vnd.oipf.dae.xhtml+xml`: MediaType = vndOipfDaeXhtmlXml

    lazy val vndOipfMippvcontrolmessageXml: MediaType =
      MediaType("application", "vnd.oipf.mippvcontrolmessage+xml", compressible = true, binary = true)
    
    lazy val `vnd.oipf.mippvcontrolmessage+xml`: MediaType = vndOipfMippvcontrolmessageXml

    lazy val vndOipfPaeGem: MediaType =
      MediaType("application", "vnd.oipf.pae.gem", compressible = false, binary = true)
    
    lazy val `vnd.oipf.pae.gem`: MediaType = vndOipfPaeGem

    lazy val vndOipfSpdiscoveryXml: MediaType =
      MediaType("application", "vnd.oipf.spdiscovery+xml", compressible = true, binary = true)
    
    lazy val `vnd.oipf.spdiscovery+xml`: MediaType = vndOipfSpdiscoveryXml

    lazy val vndOipfSpdlistXml: MediaType =
      MediaType("application", "vnd.oipf.spdlist+xml", compressible = true, binary = true)
    
    lazy val `vnd.oipf.spdlist+xml`: MediaType = vndOipfSpdlistXml

    lazy val vndOipfUeprofileXml: MediaType =
      MediaType("application", "vnd.oipf.ueprofile+xml", compressible = true, binary = true)
    
    lazy val `vnd.oipf.ueprofile+xml`: MediaType = vndOipfUeprofileXml

    lazy val vndOipfUserprofileXml: MediaType =
      MediaType("application", "vnd.oipf.userprofile+xml", compressible = true, binary = true)
    
    lazy val `vnd.oipf.userprofile+xml`: MediaType = vndOipfUserprofileXml

    lazy val vndOlpcSugar: MediaType =
      MediaType("application", "vnd.olpc-sugar", compressible = false, binary = true, fileExtensions = List("xo"))
    
    lazy val `vnd.olpc-sugar`: MediaType = vndOlpcSugar

    lazy val vndOmaScwsConfig: MediaType =
      MediaType("application", "vnd.oma-scws-config", compressible = false, binary = true)
    
    lazy val `vnd.oma-scws-config`: MediaType = vndOmaScwsConfig

    lazy val vndOmaScwsHttpRequest: MediaType =
      MediaType("application", "vnd.oma-scws-http-request", compressible = false, binary = true)
    
    lazy val `vnd.oma-scws-http-request`: MediaType = vndOmaScwsHttpRequest

    lazy val vndOmaScwsHttpResponse: MediaType =
      MediaType("application", "vnd.oma-scws-http-response", compressible = false, binary = true)
    
    lazy val `vnd.oma-scws-http-response`: MediaType = vndOmaScwsHttpResponse

    lazy val vndOmaBcastAssociatedProcedureParameterXml: MediaType =
      MediaType("application", "vnd.oma.bcast.associated-procedure-parameter+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.bcast.associated-procedure-parameter+xml`: MediaType = vndOmaBcastAssociatedProcedureParameterXml

    lazy val vndOmaBcastDrmTriggerXml: MediaType =
      MediaType("application", "vnd.oma.bcast.drm-trigger+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.bcast.drm-trigger+xml`: MediaType = vndOmaBcastDrmTriggerXml

    lazy val vndOmaBcastImdXml: MediaType =
      MediaType("application", "vnd.oma.bcast.imd+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.bcast.imd+xml`: MediaType = vndOmaBcastImdXml

    lazy val vndOmaBcastLtkm: MediaType =
      MediaType("application", "vnd.oma.bcast.ltkm", compressible = false, binary = true)
    
    lazy val `vnd.oma.bcast.ltkm`: MediaType = vndOmaBcastLtkm

    lazy val vndOmaBcastNotificationXml: MediaType =
      MediaType("application", "vnd.oma.bcast.notification+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.bcast.notification+xml`: MediaType = vndOmaBcastNotificationXml

    lazy val vndOmaBcastProvisioningtrigger: MediaType =
      MediaType("application", "vnd.oma.bcast.provisioningtrigger", compressible = false, binary = true)
    
    lazy val `vnd.oma.bcast.provisioningtrigger`: MediaType = vndOmaBcastProvisioningtrigger

    lazy val vndOmaBcastSgboot: MediaType =
      MediaType("application", "vnd.oma.bcast.sgboot", compressible = false, binary = true)
    
    lazy val `vnd.oma.bcast.sgboot`: MediaType = vndOmaBcastSgboot

    lazy val vndOmaBcastSgddXml: MediaType =
      MediaType("application", "vnd.oma.bcast.sgdd+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.bcast.sgdd+xml`: MediaType = vndOmaBcastSgddXml

    lazy val vndOmaBcastSgdu: MediaType =
      MediaType("application", "vnd.oma.bcast.sgdu", compressible = false, binary = true)
    
    lazy val `vnd.oma.bcast.sgdu`: MediaType = vndOmaBcastSgdu

    lazy val vndOmaBcastSimpleSymbolContainer: MediaType =
      MediaType("application", "vnd.oma.bcast.simple-symbol-container", compressible = false, binary = true)
    
    lazy val `vnd.oma.bcast.simple-symbol-container`: MediaType = vndOmaBcastSimpleSymbolContainer

    lazy val vndOmaBcastSmartcardTriggerXml: MediaType =
      MediaType("application", "vnd.oma.bcast.smartcard-trigger+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.bcast.smartcard-trigger+xml`: MediaType = vndOmaBcastSmartcardTriggerXml

    lazy val vndOmaBcastSprovXml: MediaType =
      MediaType("application", "vnd.oma.bcast.sprov+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.bcast.sprov+xml`: MediaType = vndOmaBcastSprovXml

    lazy val vndOmaBcastStkm: MediaType =
      MediaType("application", "vnd.oma.bcast.stkm", compressible = false, binary = true)
    
    lazy val `vnd.oma.bcast.stkm`: MediaType = vndOmaBcastStkm

    lazy val vndOmaCabAddressBookXml: MediaType =
      MediaType("application", "vnd.oma.cab-address-book+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.cab-address-book+xml`: MediaType = vndOmaCabAddressBookXml

    lazy val vndOmaCabFeatureHandlerXml: MediaType =
      MediaType("application", "vnd.oma.cab-feature-handler+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.cab-feature-handler+xml`: MediaType = vndOmaCabFeatureHandlerXml

    lazy val vndOmaCabPccXml: MediaType =
      MediaType("application", "vnd.oma.cab-pcc+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.cab-pcc+xml`: MediaType = vndOmaCabPccXml

    lazy val vndOmaCabSubsInviteXml: MediaType =
      MediaType("application", "vnd.oma.cab-subs-invite+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.cab-subs-invite+xml`: MediaType = vndOmaCabSubsInviteXml

    lazy val vndOmaCabUserPrefsXml: MediaType =
      MediaType("application", "vnd.oma.cab-user-prefs+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.cab-user-prefs+xml`: MediaType = vndOmaCabUserPrefsXml

    lazy val vndOmaDcd: MediaType =
      MediaType("application", "vnd.oma.dcd", compressible = false, binary = true)
    
    lazy val `vnd.oma.dcd`: MediaType = vndOmaDcd

    lazy val vndOmaDcdc: MediaType =
      MediaType("application", "vnd.oma.dcdc", compressible = false, binary = true)
    
    lazy val `vnd.oma.dcdc`: MediaType = vndOmaDcdc

    lazy val vndOmaDd2Xml: MediaType =
      MediaType("application", "vnd.oma.dd2+xml", compressible = true, binary = true, fileExtensions = List("dd2"))
    
    lazy val `vnd.oma.dd2+xml`: MediaType = vndOmaDd2Xml

    lazy val vndOmaDrmRisdXml: MediaType =
      MediaType("application", "vnd.oma.drm.risd+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.drm.risd+xml`: MediaType = vndOmaDrmRisdXml

    lazy val vndOmaGroupUsageListXml: MediaType =
      MediaType("application", "vnd.oma.group-usage-list+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.group-usage-list+xml`: MediaType = vndOmaGroupUsageListXml

    lazy val vndOmaLwm2mCbor: MediaType =
      MediaType("application", "vnd.oma.lwm2m+cbor", compressible = false, binary = true)
    
    lazy val `vnd.oma.lwm2m+cbor`: MediaType = vndOmaLwm2mCbor

    lazy val vndOmaLwm2mJson: MediaType =
      MediaType("application", "vnd.oma.lwm2m+json", compressible = true, binary = false)
    
    lazy val `vnd.oma.lwm2m+json`: MediaType = vndOmaLwm2mJson

    lazy val vndOmaLwm2mTlv: MediaType =
      MediaType("application", "vnd.oma.lwm2m+tlv", compressible = false, binary = true)
    
    lazy val `vnd.oma.lwm2m+tlv`: MediaType = vndOmaLwm2mTlv

    lazy val vndOmaPalXml: MediaType =
      MediaType("application", "vnd.oma.pal+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.pal+xml`: MediaType = vndOmaPalXml

    lazy val vndOmaPocDetailedProgressReportXml: MediaType =
      MediaType("application", "vnd.oma.poc.detailed-progress-report+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.poc.detailed-progress-report+xml`: MediaType = vndOmaPocDetailedProgressReportXml

    lazy val vndOmaPocFinalReportXml: MediaType =
      MediaType("application", "vnd.oma.poc.final-report+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.poc.final-report+xml`: MediaType = vndOmaPocFinalReportXml

    lazy val vndOmaPocGroupsXml: MediaType =
      MediaType("application", "vnd.oma.poc.groups+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.poc.groups+xml`: MediaType = vndOmaPocGroupsXml

    lazy val vndOmaPocInvocationDescriptorXml: MediaType =
      MediaType("application", "vnd.oma.poc.invocation-descriptor+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.poc.invocation-descriptor+xml`: MediaType = vndOmaPocInvocationDescriptorXml

    lazy val vndOmaPocOptimizedProgressReportXml: MediaType =
      MediaType("application", "vnd.oma.poc.optimized-progress-report+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.poc.optimized-progress-report+xml`: MediaType = vndOmaPocOptimizedProgressReportXml

    lazy val vndOmaPush: MediaType =
      MediaType("application", "vnd.oma.push", compressible = false, binary = true)
    
    lazy val `vnd.oma.push`: MediaType = vndOmaPush

    lazy val vndOmaScidmMessagesXml: MediaType =
      MediaType("application", "vnd.oma.scidm.messages+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.scidm.messages+xml`: MediaType = vndOmaScidmMessagesXml

    lazy val vndOmaXcapDirectoryXml: MediaType =
      MediaType("application", "vnd.oma.xcap-directory+xml", compressible = true, binary = true)
    
    lazy val `vnd.oma.xcap-directory+xml`: MediaType = vndOmaXcapDirectoryXml

    lazy val vndOmadsEmailXml: MediaType =
      MediaType("application", "vnd.omads-email+xml", compressible = true, binary = true)
    
    lazy val `vnd.omads-email+xml`: MediaType = vndOmadsEmailXml

    lazy val vndOmadsFileXml: MediaType =
      MediaType("application", "vnd.omads-file+xml", compressible = true, binary = true)
    
    lazy val `vnd.omads-file+xml`: MediaType = vndOmadsFileXml

    lazy val vndOmadsFolderXml: MediaType =
      MediaType("application", "vnd.omads-folder+xml", compressible = true, binary = true)
    
    lazy val `vnd.omads-folder+xml`: MediaType = vndOmadsFolderXml

    lazy val vndOmalocSuplInit: MediaType =
      MediaType("application", "vnd.omaloc-supl-init", compressible = false, binary = true)
    
    lazy val `vnd.omaloc-supl-init`: MediaType = vndOmalocSuplInit

    lazy val vndOmsCellularCoseContentCbor: MediaType =
      MediaType("application", "vnd.oms.cellular-cose-content+cbor", compressible = false, binary = true)
    
    lazy val `vnd.oms.cellular-cose-content+cbor`: MediaType = vndOmsCellularCoseContentCbor

    lazy val vndOnepager: MediaType =
      MediaType("application", "vnd.onepager", compressible = false, binary = true)
    
    lazy val `vnd.onepager`: MediaType = vndOnepager

    lazy val vndOnepagertamp: MediaType =
      MediaType("application", "vnd.onepagertamp", compressible = false, binary = true)
    
    lazy val `vnd.onepagertamp`: MediaType = vndOnepagertamp

    lazy val vndOnepagertamx: MediaType =
      MediaType("application", "vnd.onepagertamx", compressible = false, binary = true)
    
    lazy val `vnd.onepagertamx`: MediaType = vndOnepagertamx

    lazy val vndOnepagertat: MediaType =
      MediaType("application", "vnd.onepagertat", compressible = false, binary = true)
    
    lazy val `vnd.onepagertat`: MediaType = vndOnepagertat

    lazy val vndOnepagertatp: MediaType =
      MediaType("application", "vnd.onepagertatp", compressible = false, binary = true)
    
    lazy val `vnd.onepagertatp`: MediaType = vndOnepagertatp

    lazy val vndOnepagertatx: MediaType =
      MediaType("application", "vnd.onepagertatx", compressible = false, binary = true)
    
    lazy val `vnd.onepagertatx`: MediaType = vndOnepagertatx

    lazy val vndOnvifMetadata: MediaType =
      MediaType("application", "vnd.onvif.metadata", compressible = false, binary = true)
    
    lazy val `vnd.onvif.metadata`: MediaType = vndOnvifMetadata

    lazy val vndOpenbloxGameXml: MediaType =
      MediaType("application", "vnd.openblox.game+xml", compressible = true, binary = true, fileExtensions = List("obgx"))
    
    lazy val `vnd.openblox.game+xml`: MediaType = vndOpenbloxGameXml

    lazy val vndOpenbloxGameBinary: MediaType =
      MediaType("application", "vnd.openblox.game-binary", compressible = false, binary = true)
    
    lazy val `vnd.openblox.game-binary`: MediaType = vndOpenbloxGameBinary

    lazy val vndOpeneyeOeb: MediaType =
      MediaType("application", "vnd.openeye.oeb", compressible = false, binary = true)
    
    lazy val `vnd.openeye.oeb`: MediaType = vndOpeneyeOeb

    lazy val vndOpenofficeorgExtension: MediaType =
      MediaType("application", "vnd.openofficeorg.extension", compressible = false, binary = true, fileExtensions = List("oxt"))
    
    lazy val `vnd.openofficeorg.extension`: MediaType = vndOpenofficeorgExtension

    lazy val vndOpenprinttag: MediaType =
      MediaType("application", "vnd.openprinttag", compressible = false, binary = true)
    
    lazy val `vnd.openprinttag`: MediaType = vndOpenprinttag

    lazy val vndOpenstreetmapDataXml: MediaType =
      MediaType("application", "vnd.openstreetmap.data+xml", compressible = true, binary = true, fileExtensions = List("osm"))
    
    lazy val `vnd.openstreetmap.data+xml`: MediaType = vndOpenstreetmapDataXml

    lazy val vndOpentimestampsOts: MediaType =
      MediaType("application", "vnd.opentimestamps.ots", compressible = false, binary = true)
    
    lazy val `vnd.opentimestamps.ots`: MediaType = vndOpentimestampsOts

    lazy val vndOpenvpiDspxJson: MediaType =
      MediaType("application", "vnd.openvpi.dspx+json", compressible = true, binary = false)
    
    lazy val `vnd.openvpi.dspx+json`: MediaType = vndOpenvpiDspxJson

    lazy val vndOpenxmlformatsOfficedocumentCustomPropertiesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.custom-properties+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.custom-properties+xml`: MediaType = vndOpenxmlformatsOfficedocumentCustomPropertiesXml

    lazy val vndOpenxmlformatsOfficedocumentCustomxmlpropertiesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.customxmlproperties+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.customxmlproperties+xml`: MediaType = vndOpenxmlformatsOfficedocumentCustomxmlpropertiesXml

    lazy val vndOpenxmlformatsOfficedocumentDrawingXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.drawing+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.drawing+xml`: MediaType = vndOpenxmlformatsOfficedocumentDrawingXml

    lazy val vndOpenxmlformatsOfficedocumentDrawingmlChartXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.drawingml.chart+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.drawingml.chart+xml`: MediaType = vndOpenxmlformatsOfficedocumentDrawingmlChartXml

    lazy val vndOpenxmlformatsOfficedocumentDrawingmlChartshapesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.drawingml.chartshapes+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.drawingml.chartshapes+xml`: MediaType = vndOpenxmlformatsOfficedocumentDrawingmlChartshapesXml

    lazy val vndOpenxmlformatsOfficedocumentDrawingmlDiagramcolorsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.drawingml.diagramcolors+xml`: MediaType = vndOpenxmlformatsOfficedocumentDrawingmlDiagramcolorsXml

    lazy val vndOpenxmlformatsOfficedocumentDrawingmlDiagramdataXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.drawingml.diagramdata+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.drawingml.diagramdata+xml`: MediaType = vndOpenxmlformatsOfficedocumentDrawingmlDiagramdataXml

    lazy val vndOpenxmlformatsOfficedocumentDrawingmlDiagramlayoutXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.drawingml.diagramlayout+xml`: MediaType = vndOpenxmlformatsOfficedocumentDrawingmlDiagramlayoutXml

    lazy val vndOpenxmlformatsOfficedocumentDrawingmlDiagramstyleXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.drawingml.diagramstyle+xml`: MediaType = vndOpenxmlformatsOfficedocumentDrawingmlDiagramstyleXml

    lazy val vndOpenxmlformatsOfficedocumentExtendedPropertiesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.extended-properties+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.extended-properties+xml`: MediaType = vndOpenxmlformatsOfficedocumentExtendedPropertiesXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlCommentauthorsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.commentauthors+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.commentauthors+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlCommentauthorsXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlCommentsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.comments+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.comments+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlCommentsXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlHandoutmasterXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.handoutmaster+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlHandoutmasterXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlNotesmasterXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.notesmaster+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.notesmaster+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlNotesmasterXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlNotesslideXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.notesslide+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.notesslide+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlNotesslideXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlPresentation: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.presentation", compressible = false, binary = true, fileExtensions = List("pptx"))
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.presentation`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlPresentation

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlPresentationMainXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.presentation.main+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.presentation.main+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlPresentationMainXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlPrespropsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.presprops+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.presprops+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlPrespropsXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlSlide: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.slide", compressible = false, binary = true, fileExtensions = List("sldx"))
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.slide`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlSlide

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlSlideXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.slide+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.slide+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlSlideXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlSlidelayoutXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.slidelayout+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.slidelayout+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlSlidelayoutXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlSlidemasterXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.slidemaster+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.slidemaster+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlSlidemasterXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlSlideshow: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.slideshow", compressible = false, binary = true, fileExtensions = List("ppsx"))
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.slideshow`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlSlideshow

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlSlideshowMainXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.slideshow.main+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlSlideshowMainXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlSlideupdateinfoXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.slideupdateinfo+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlSlideupdateinfoXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlTablestylesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.tablestyles+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.tablestyles+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlTablestylesXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlTagsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.tags+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.tags+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlTagsXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlTemplate: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.template", compressible = false, binary = true, fileExtensions = List("potx"))
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.template`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlTemplate

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlTemplateMainXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.template.main+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.template.main+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlTemplateMainXml

    lazy val vndOpenxmlformatsOfficedocumentPresentationmlViewpropsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.presentationml.viewprops+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.presentationml.viewprops+xml`: MediaType = vndOpenxmlformatsOfficedocumentPresentationmlViewpropsXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlCalcchainXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.calcchain+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlCalcchainXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlChartsheetXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlChartsheetXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlCommentsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.comments+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.comments+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlCommentsXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlConnectionsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.connections+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.connections+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlConnectionsXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlDialogsheetXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.dialogsheet+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlDialogsheetXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlExternallinkXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.externallink+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlExternallinkXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlPivotcachedefinitionXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.pivotcachedefinition+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlPivotcachedefinitionXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlPivotcacherecordsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.pivotcacherecords+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlPivotcacherecordsXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlPivottableXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.pivottable+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlPivottableXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlQuerytableXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.querytable+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlQuerytableXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlRevisionheadersXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.revisionheaders+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlRevisionheadersXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlRevisionlogXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.revisionlog+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlRevisionlogXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlSharedstringsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.sharedstrings+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlSharedstringsXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlSheet: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet", compressible = false, binary = true, fileExtensions = List("xlsx"))
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.sheet`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlSheet

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlSheetMainXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlSheetMainXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlSheetmetadataXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.sheetmetadata+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlSheetmetadataXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlStylesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.styles+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.styles+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlStylesXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlTableXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.table+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.table+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlTableXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlTablesinglecellsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.tablesinglecells+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlTablesinglecellsXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlTemplate: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.template", compressible = false, binary = true, fileExtensions = List("xltx"))
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.template`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlTemplate

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlTemplateMainXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlTemplateMainXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlUsernamesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.usernames+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlUsernamesXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlVolatiledependenciesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.volatiledependencies+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlVolatiledependenciesXml

    lazy val vndOpenxmlformatsOfficedocumentSpreadsheetmlWorksheetXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml`: MediaType = vndOpenxmlformatsOfficedocumentSpreadsheetmlWorksheetXml

    lazy val vndOpenxmlformatsOfficedocumentThemeXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.theme+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.theme+xml`: MediaType = vndOpenxmlformatsOfficedocumentThemeXml

    lazy val vndOpenxmlformatsOfficedocumentThemeoverrideXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.themeoverride+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.themeoverride+xml`: MediaType = vndOpenxmlformatsOfficedocumentThemeoverrideXml

    lazy val vndOpenxmlformatsOfficedocumentVmldrawing: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.vmldrawing", compressible = false, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.vmldrawing`: MediaType = vndOpenxmlformatsOfficedocumentVmldrawing

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlCommentsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.comments+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.comments+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlCommentsXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlDocument: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.document", compressible = false, binary = true, fileExtensions = List("docx"))
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.document`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlDocument

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlDocumentGlossaryXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlDocumentGlossaryXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlDocumentMainXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlDocumentMainXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlEndnotesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlEndnotesXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlFonttableXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.fonttable+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlFonttableXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlFooterXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.footer+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.footer+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlFooterXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlFootnotesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlFootnotesXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlNumberingXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlNumberingXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlSettingsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.settings+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.settings+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlSettingsXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlStylesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.styles+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.styles+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlStylesXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlTemplate: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.template", compressible = false, binary = true, fileExtensions = List("dotx"))
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.template`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlTemplate

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlTemplateMainXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlTemplateMainXml

    lazy val vndOpenxmlformatsOfficedocumentWordprocessingmlWebsettingsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-officedocument.wordprocessingml.websettings+xml`: MediaType = vndOpenxmlformatsOfficedocumentWordprocessingmlWebsettingsXml

    lazy val vndOpenxmlformatsPackageCorePropertiesXml: MediaType =
      MediaType("application", "vnd.openxmlformats-package.core-properties+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-package.core-properties+xml`: MediaType = vndOpenxmlformatsPackageCorePropertiesXml

    lazy val vndOpenxmlformatsPackageDigitalSignatureXmlsignatureXml: MediaType =
      MediaType("application", "vnd.openxmlformats-package.digital-signature-xmlsignature+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-package.digital-signature-xmlsignature+xml`: MediaType = vndOpenxmlformatsPackageDigitalSignatureXmlsignatureXml

    lazy val vndOpenxmlformatsPackageRelationshipsXml: MediaType =
      MediaType("application", "vnd.openxmlformats-package.relationships+xml", compressible = true, binary = true)
    
    lazy val `vnd.openxmlformats-package.relationships+xml`: MediaType = vndOpenxmlformatsPackageRelationshipsXml

    lazy val vndOracleResourceJson: MediaType =
      MediaType("application", "vnd.oracle.resource+json", compressible = true, binary = false)
    
    lazy val `vnd.oracle.resource+json`: MediaType = vndOracleResourceJson

    lazy val vndOrangeIndata: MediaType =
      MediaType("application", "vnd.orange.indata", compressible = false, binary = true)
    
    lazy val `vnd.orange.indata`: MediaType = vndOrangeIndata

    lazy val vndOsaNetdeploy: MediaType =
      MediaType("application", "vnd.osa.netdeploy", compressible = false, binary = true)
    
    lazy val `vnd.osa.netdeploy`: MediaType = vndOsaNetdeploy

    lazy val vndOsgeoMapguidePackage: MediaType =
      MediaType("application", "vnd.osgeo.mapguide.package", compressible = false, binary = true, fileExtensions = List("mgp"))
    
    lazy val `vnd.osgeo.mapguide.package`: MediaType = vndOsgeoMapguidePackage

    lazy val vndOsgiBundle: MediaType =
      MediaType("application", "vnd.osgi.bundle", compressible = false, binary = true)
    
    lazy val `vnd.osgi.bundle`: MediaType = vndOsgiBundle

    lazy val vndOsgiDp: MediaType =
      MediaType("application", "vnd.osgi.dp", compressible = false, binary = true, fileExtensions = List("dp"))
    
    lazy val `vnd.osgi.dp`: MediaType = vndOsgiDp

    lazy val vndOsgiSubsystem: MediaType =
      MediaType("application", "vnd.osgi.subsystem", compressible = false, binary = true, fileExtensions = List("esa"))
    
    lazy val `vnd.osgi.subsystem`: MediaType = vndOsgiSubsystem

    lazy val vndOtpsCtKipXml: MediaType =
      MediaType("application", "vnd.otps.ct-kip+xml", compressible = true, binary = true)
    
    lazy val `vnd.otps.ct-kip+xml`: MediaType = vndOtpsCtKipXml

    lazy val vndOxliCountgraph: MediaType =
      MediaType("application", "vnd.oxli.countgraph", compressible = false, binary = true)
    
    lazy val `vnd.oxli.countgraph`: MediaType = vndOxliCountgraph

    lazy val vndPagerdutyJson: MediaType =
      MediaType("application", "vnd.pagerduty+json", compressible = true, binary = false)
    
    lazy val `vnd.pagerduty+json`: MediaType = vndPagerdutyJson

    lazy val vndPalm: MediaType =
      MediaType("application", "vnd.palm", compressible = false, binary = true, fileExtensions = List("pdb", "pqa", "oprc"))
    
    lazy val `vnd.palm`: MediaType = vndPalm

    lazy val vndPanoply: MediaType =
      MediaType("application", "vnd.panoply", compressible = false, binary = true)
    
    lazy val `vnd.panoply`: MediaType = vndPanoply

    lazy val vndPaosXml: MediaType =
      MediaType("application", "vnd.paos.xml", compressible = false, binary = true)
    
    lazy val `vnd.paos.xml`: MediaType = vndPaosXml

    lazy val vndPatentdive: MediaType =
      MediaType("application", "vnd.patentdive", compressible = false, binary = true)
    
    lazy val `vnd.patentdive`: MediaType = vndPatentdive

    lazy val vndPatientecommsdoc: MediaType =
      MediaType("application", "vnd.patientecommsdoc", compressible = false, binary = true)
    
    lazy val `vnd.patientecommsdoc`: MediaType = vndPatientecommsdoc

    lazy val vndPawaafile: MediaType =
      MediaType("application", "vnd.pawaafile", compressible = false, binary = true, fileExtensions = List("paw"))
    
    lazy val `vnd.pawaafile`: MediaType = vndPawaafile

    lazy val vndPcos: MediaType =
      MediaType("application", "vnd.pcos", compressible = false, binary = true)
    
    lazy val `vnd.pcos`: MediaType = vndPcos

    lazy val vndPgFormat: MediaType =
      MediaType("application", "vnd.pg.format", compressible = false, binary = true, fileExtensions = List("str"))
    
    lazy val `vnd.pg.format`: MediaType = vndPgFormat

    lazy val vndPgOsasli: MediaType =
      MediaType("application", "vnd.pg.osasli", compressible = false, binary = true, fileExtensions = List("ei6"))
    
    lazy val `vnd.pg.osasli`: MediaType = vndPgOsasli

    lazy val vndPiaccessApplicationLicence: MediaType =
      MediaType("application", "vnd.piaccess.application-licence", compressible = false, binary = true)
    
    lazy val `vnd.piaccess.application-licence`: MediaType = vndPiaccessApplicationLicence

    lazy val vndPicsel: MediaType =
      MediaType("application", "vnd.picsel", compressible = false, binary = true, fileExtensions = List("efif"))
    
    lazy val `vnd.picsel`: MediaType = vndPicsel

    lazy val vndPmiWidget: MediaType =
      MediaType("application", "vnd.pmi.widget", compressible = false, binary = true, fileExtensions = List("wg"))
    
    lazy val `vnd.pmi.widget`: MediaType = vndPmiWidget

    lazy val vndPmtiles: MediaType =
      MediaType("application", "vnd.pmtiles", compressible = false, binary = true)
    
    lazy val `vnd.pmtiles`: MediaType = vndPmtiles

    lazy val vndPocGroupAdvertisementXml: MediaType =
      MediaType("application", "vnd.poc.group-advertisement+xml", compressible = true, binary = true)
    
    lazy val `vnd.poc.group-advertisement+xml`: MediaType = vndPocGroupAdvertisementXml

    lazy val vndPocketlearn: MediaType =
      MediaType("application", "vnd.pocketlearn", compressible = false, binary = true, fileExtensions = List("plf"))
    
    lazy val `vnd.pocketlearn`: MediaType = vndPocketlearn

    lazy val vndPowerbuilder6: MediaType =
      MediaType("application", "vnd.powerbuilder6", compressible = false, binary = true, fileExtensions = List("pbd"))
    
    lazy val `vnd.powerbuilder6`: MediaType = vndPowerbuilder6

    lazy val vndPowerbuilder6S: MediaType =
      MediaType("application", "vnd.powerbuilder6-s", compressible = false, binary = true)
    
    lazy val `vnd.powerbuilder6-s`: MediaType = vndPowerbuilder6S

    lazy val vndPowerbuilder7: MediaType =
      MediaType("application", "vnd.powerbuilder7", compressible = false, binary = true)
    
    lazy val `vnd.powerbuilder7`: MediaType = vndPowerbuilder7

    lazy val vndPowerbuilder7S: MediaType =
      MediaType("application", "vnd.powerbuilder7-s", compressible = false, binary = true)
    
    lazy val `vnd.powerbuilder7-s`: MediaType = vndPowerbuilder7S

    lazy val vndPowerbuilder75: MediaType =
      MediaType("application", "vnd.powerbuilder75", compressible = false, binary = true)
    
    lazy val `vnd.powerbuilder75`: MediaType = vndPowerbuilder75

    lazy val vndPowerbuilder75S: MediaType =
      MediaType("application", "vnd.powerbuilder75-s", compressible = false, binary = true)
    
    lazy val `vnd.powerbuilder75-s`: MediaType = vndPowerbuilder75S

    lazy val vndPpSystemverifyXml: MediaType =
      MediaType("application", "vnd.pp.systemverify+xml", compressible = true, binary = true, fileExtensions = List("systemverify"))
    
    lazy val `vnd.pp.systemverify+xml`: MediaType = vndPpSystemverifyXml

    lazy val vndPreminet: MediaType =
      MediaType("application", "vnd.preminet", compressible = false, binary = true)
    
    lazy val `vnd.preminet`: MediaType = vndPreminet

    lazy val vndPreviewsystemsBox: MediaType =
      MediaType("application", "vnd.previewsystems.box", compressible = false, binary = true, fileExtensions = List("box"))
    
    lazy val `vnd.previewsystems.box`: MediaType = vndPreviewsystemsBox

    lazy val vndProcreateBrush: MediaType =
      MediaType("application", "vnd.procreate.brush", compressible = false, binary = true, fileExtensions = List("brush"))
    
    lazy val `vnd.procreate.brush`: MediaType = vndProcreateBrush

    lazy val vndProcreateBrushset: MediaType =
      MediaType("application", "vnd.procreate.brushset", compressible = false, binary = true, fileExtensions = List("brushset"))
    
    lazy val `vnd.procreate.brushset`: MediaType = vndProcreateBrushset

    lazy val vndProcreateDream: MediaType =
      MediaType("application", "vnd.procreate.dream", compressible = false, binary = true, fileExtensions = List("drm"))
    
    lazy val `vnd.procreate.dream`: MediaType = vndProcreateDream

    lazy val vndProjectGraph: MediaType =
      MediaType("application", "vnd.project-graph", compressible = false, binary = true)
    
    lazy val `vnd.project-graph`: MediaType = vndProjectGraph

    lazy val vndProteusMagazine: MediaType =
      MediaType("application", "vnd.proteus.magazine", compressible = false, binary = true, fileExtensions = List("mgz"))
    
    lazy val `vnd.proteus.magazine`: MediaType = vndProteusMagazine

    lazy val vndPsfs: MediaType =
      MediaType("application", "vnd.psfs", compressible = false, binary = true)
    
    lazy val `vnd.psfs`: MediaType = vndPsfs

    lazy val vndPtMundusmundi: MediaType =
      MediaType("application", "vnd.pt.mundusmundi", compressible = false, binary = true)
    
    lazy val `vnd.pt.mundusmundi`: MediaType = vndPtMundusmundi

    lazy val vndPublishareDeltaTree: MediaType =
      MediaType("application", "vnd.publishare-delta-tree", compressible = false, binary = true, fileExtensions = List("qps"))
    
    lazy val `vnd.publishare-delta-tree`: MediaType = vndPublishareDeltaTree

    lazy val vndPviPtid1: MediaType =
      MediaType("application", "vnd.pvi.ptid1", compressible = false, binary = true, fileExtensions = List("ptid"))
    
    lazy val `vnd.pvi.ptid1`: MediaType = vndPviPtid1

    lazy val vndPwgMultiplexed: MediaType =
      MediaType("application", "vnd.pwg-multiplexed", compressible = false, binary = true)
    
    lazy val `vnd.pwg-multiplexed`: MediaType = vndPwgMultiplexed

    lazy val vndPwgXhtmlPrintXml: MediaType =
      MediaType("application", "vnd.pwg-xhtml-print+xml", compressible = true, binary = true, fileExtensions = List("xhtm"))
    
    lazy val `vnd.pwg-xhtml-print+xml`: MediaType = vndPwgXhtmlPrintXml

    lazy val vndPyonJson: MediaType =
      MediaType("application", "vnd.pyon+json", compressible = true, binary = false)
    
    lazy val `vnd.pyon+json`: MediaType = vndPyonJson

    lazy val vndQualcommBrewAppRes: MediaType =
      MediaType("application", "vnd.qualcomm.brew-app-res", compressible = false, binary = true)
    
    lazy val `vnd.qualcomm.brew-app-res`: MediaType = vndQualcommBrewAppRes

    lazy val vndQuarantainenet: MediaType =
      MediaType("application", "vnd.quarantainenet", compressible = false, binary = true)
    
    lazy val `vnd.quarantainenet`: MediaType = vndQuarantainenet

    lazy val vndQuarkQuarkxpress: MediaType =
      MediaType("application", "vnd.quark.quarkxpress", compressible = false, binary = true, fileExtensions = List("qxd", "qxt", "qwd", "qwt", "qxl", "qxb"))
    
    lazy val `vnd.quark.quarkxpress`: MediaType = vndQuarkQuarkxpress

    lazy val vndQuobjectQuoxdocument: MediaType =
      MediaType("application", "vnd.quobject-quoxdocument", compressible = false, binary = true)
    
    lazy val `vnd.quobject-quoxdocument`: MediaType = vndQuobjectQuoxdocument

    lazy val vndR74nSandboxelsJson: MediaType =
      MediaType("application", "vnd.r74n.sandboxels+json", compressible = true, binary = false)
    
    lazy val `vnd.r74n.sandboxels+json`: MediaType = vndR74nSandboxelsJson

    lazy val vndRadisysMomlXml: MediaType =
      MediaType("application", "vnd.radisys.moml+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.moml+xml`: MediaType = vndRadisysMomlXml

    lazy val vndRadisysMsmlXml: MediaType =
      MediaType("application", "vnd.radisys.msml+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml+xml`: MediaType = vndRadisysMsmlXml

    lazy val vndRadisysMsmlAuditXml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-audit+xml`: MediaType = vndRadisysMsmlAuditXml

    lazy val vndRadisysMsmlAuditConfXml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-conf+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-audit-conf+xml`: MediaType = vndRadisysMsmlAuditConfXml

    lazy val vndRadisysMsmlAuditConnXml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-conn+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-audit-conn+xml`: MediaType = vndRadisysMsmlAuditConnXml

    lazy val vndRadisysMsmlAuditDialogXml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-dialog+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-audit-dialog+xml`: MediaType = vndRadisysMsmlAuditDialogXml

    lazy val vndRadisysMsmlAuditStreamXml: MediaType =
      MediaType("application", "vnd.radisys.msml-audit-stream+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-audit-stream+xml`: MediaType = vndRadisysMsmlAuditStreamXml

    lazy val vndRadisysMsmlConfXml: MediaType =
      MediaType("application", "vnd.radisys.msml-conf+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-conf+xml`: MediaType = vndRadisysMsmlConfXml

    lazy val vndRadisysMsmlDialogXml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-dialog+xml`: MediaType = vndRadisysMsmlDialogXml

    lazy val vndRadisysMsmlDialogBaseXml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-base+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-dialog-base+xml`: MediaType = vndRadisysMsmlDialogBaseXml

    lazy val vndRadisysMsmlDialogFaxDetectXml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-fax-detect+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-dialog-fax-detect+xml`: MediaType = vndRadisysMsmlDialogFaxDetectXml

    lazy val vndRadisysMsmlDialogFaxSendrecvXml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-fax-sendrecv+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-dialog-fax-sendrecv+xml`: MediaType = vndRadisysMsmlDialogFaxSendrecvXml

    lazy val vndRadisysMsmlDialogGroupXml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-group+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-dialog-group+xml`: MediaType = vndRadisysMsmlDialogGroupXml

    lazy val vndRadisysMsmlDialogSpeechXml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-speech+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-dialog-speech+xml`: MediaType = vndRadisysMsmlDialogSpeechXml

    lazy val vndRadisysMsmlDialogTransformXml: MediaType =
      MediaType("application", "vnd.radisys.msml-dialog-transform+xml", compressible = true, binary = true)
    
    lazy val `vnd.radisys.msml-dialog-transform+xml`: MediaType = vndRadisysMsmlDialogTransformXml

    lazy val vndRainstorData: MediaType =
      MediaType("application", "vnd.rainstor.data", compressible = false, binary = true)
    
    lazy val `vnd.rainstor.data`: MediaType = vndRainstorData

    lazy val vndRapid: MediaType =
      MediaType("application", "vnd.rapid", compressible = false, binary = true)
    
    lazy val `vnd.rapid`: MediaType = vndRapid

    lazy val vndRar: MediaType =
      MediaType("application", "vnd.rar", compressible = false, binary = true, fileExtensions = List("rar"))
    
    lazy val `vnd.rar`: MediaType = vndRar

    lazy val vndRealvncBed: MediaType =
      MediaType("application", "vnd.realvnc.bed", compressible = false, binary = true, fileExtensions = List("bed"))
    
    lazy val `vnd.realvnc.bed`: MediaType = vndRealvncBed

    lazy val vndRecordareMusicxml: MediaType =
      MediaType("application", "vnd.recordare.musicxml", compressible = false, binary = true, fileExtensions = List("mxl"))
    
    lazy val `vnd.recordare.musicxml`: MediaType = vndRecordareMusicxml

    lazy val vndRecordareMusicxmlXml: MediaType =
      MediaType("application", "vnd.recordare.musicxml+xml", compressible = true, binary = true, fileExtensions = List("musicxml"))
    
    lazy val `vnd.recordare.musicxml+xml`: MediaType = vndRecordareMusicxmlXml

    lazy val vndRelpipe: MediaType =
      MediaType("application", "vnd.relpipe", compressible = false, binary = true)
    
    lazy val `vnd.relpipe`: MediaType = vndRelpipe

    lazy val vndRenlearnRlprint: MediaType =
      MediaType("application", "vnd.renlearn.rlprint", compressible = false, binary = true)
    
    lazy val `vnd.renlearn.rlprint`: MediaType = vndRenlearnRlprint

    lazy val vndResilientLogic: MediaType =
      MediaType("application", "vnd.resilient.logic", compressible = false, binary = true)
    
    lazy val `vnd.resilient.logic`: MediaType = vndResilientLogic

    lazy val vndRestfulJson: MediaType =
      MediaType("application", "vnd.restful+json", compressible = true, binary = false)
    
    lazy val `vnd.restful+json`: MediaType = vndRestfulJson

    lazy val vndRigCryptonote: MediaType =
      MediaType("application", "vnd.rig.cryptonote", compressible = false, binary = true, fileExtensions = List("cryptonote"))
    
    lazy val `vnd.rig.cryptonote`: MediaType = vndRigCryptonote

    lazy val vndRimCod: MediaType =
      MediaType("application", "vnd.rim.cod", compressible = false, binary = true, fileExtensions = List("cod"))
    
    lazy val `vnd.rim.cod`: MediaType = vndRimCod

    lazy val vndRnRealmedia: MediaType =
      MediaType("application", "vnd.rn-realmedia", compressible = false, binary = true, fileExtensions = List("rm"))
    
    lazy val `vnd.rn-realmedia`: MediaType = vndRnRealmedia

    lazy val vndRnRealmediaVbr: MediaType =
      MediaType("application", "vnd.rn-realmedia-vbr", compressible = false, binary = true, fileExtensions = List("rmvb"))
    
    lazy val `vnd.rn-realmedia-vbr`: MediaType = vndRnRealmediaVbr

    lazy val vndRoute66Link66Xml: MediaType =
      MediaType("application", "vnd.route66.link66+xml", compressible = true, binary = true, fileExtensions = List("link66"))
    
    lazy val `vnd.route66.link66+xml`: MediaType = vndRoute66Link66Xml

    lazy val vndRs274x: MediaType =
      MediaType("application", "vnd.rs-274x", compressible = false, binary = true)
    
    lazy val `vnd.rs-274x`: MediaType = vndRs274x

    lazy val vndRuckusDownload: MediaType =
      MediaType("application", "vnd.ruckus.download", compressible = false, binary = true)
    
    lazy val `vnd.ruckus.download`: MediaType = vndRuckusDownload

    lazy val vndS3sms: MediaType =
      MediaType("application", "vnd.s3sms", compressible = false, binary = true)
    
    lazy val `vnd.s3sms`: MediaType = vndS3sms

    lazy val vndSailingtrackerTrack: MediaType =
      MediaType("application", "vnd.sailingtracker.track", compressible = false, binary = true, fileExtensions = List("st"))
    
    lazy val `vnd.sailingtracker.track`: MediaType = vndSailingtrackerTrack

    lazy val vndSar: MediaType =
      MediaType("application", "vnd.sar", compressible = false, binary = true)
    
    lazy val `vnd.sar`: MediaType = vndSar

    lazy val vndSbmCid: MediaType =
      MediaType("application", "vnd.sbm.cid", compressible = false, binary = true)
    
    lazy val `vnd.sbm.cid`: MediaType = vndSbmCid

    lazy val vndSbmMid2: MediaType =
      MediaType("application", "vnd.sbm.mid2", compressible = false, binary = true)
    
    lazy val `vnd.sbm.mid2`: MediaType = vndSbmMid2

    lazy val vndScribus: MediaType =
      MediaType("application", "vnd.scribus", compressible = false, binary = true)
    
    lazy val `vnd.scribus`: MediaType = vndScribus

    lazy val vndSealed3df: MediaType =
      MediaType("application", "vnd.sealed.3df", compressible = false, binary = true)
    
    lazy val `vnd.sealed.3df`: MediaType = vndSealed3df

    lazy val vndSealedCsf: MediaType =
      MediaType("application", "vnd.sealed.csf", compressible = false, binary = true)
    
    lazy val `vnd.sealed.csf`: MediaType = vndSealedCsf

    lazy val vndSealedDoc: MediaType =
      MediaType("application", "vnd.sealed.doc", compressible = false, binary = true)
    
    lazy val `vnd.sealed.doc`: MediaType = vndSealedDoc

    lazy val vndSealedEml: MediaType =
      MediaType("application", "vnd.sealed.eml", compressible = false, binary = true)
    
    lazy val `vnd.sealed.eml`: MediaType = vndSealedEml

    lazy val vndSealedMht: MediaType =
      MediaType("application", "vnd.sealed.mht", compressible = false, binary = true)
    
    lazy val `vnd.sealed.mht`: MediaType = vndSealedMht

    lazy val vndSealedNet: MediaType =
      MediaType("application", "vnd.sealed.net", compressible = false, binary = true)
    
    lazy val `vnd.sealed.net`: MediaType = vndSealedNet

    lazy val vndSealedPpt: MediaType =
      MediaType("application", "vnd.sealed.ppt", compressible = false, binary = true)
    
    lazy val `vnd.sealed.ppt`: MediaType = vndSealedPpt

    lazy val vndSealedTiff: MediaType =
      MediaType("application", "vnd.sealed.tiff", compressible = false, binary = true)
    
    lazy val `vnd.sealed.tiff`: MediaType = vndSealedTiff

    lazy val vndSealedXls: MediaType =
      MediaType("application", "vnd.sealed.xls", compressible = false, binary = true)
    
    lazy val `vnd.sealed.xls`: MediaType = vndSealedXls

    lazy val vndSealedmediaSoftsealHtml: MediaType =
      MediaType("application", "vnd.sealedmedia.softseal.html", compressible = false, binary = true)
    
    lazy val `vnd.sealedmedia.softseal.html`: MediaType = vndSealedmediaSoftsealHtml

    lazy val vndSealedmediaSoftsealPdf: MediaType =
      MediaType("application", "vnd.sealedmedia.softseal.pdf", compressible = false, binary = true)
    
    lazy val `vnd.sealedmedia.softseal.pdf`: MediaType = vndSealedmediaSoftsealPdf

    lazy val vndSeemail: MediaType =
      MediaType("application", "vnd.seemail", compressible = false, binary = true, fileExtensions = List("see"))
    
    lazy val `vnd.seemail`: MediaType = vndSeemail

    lazy val vndSeisJson: MediaType =
      MediaType("application", "vnd.seis+json", compressible = true, binary = false)
    
    lazy val `vnd.seis+json`: MediaType = vndSeisJson

    lazy val vndSema: MediaType =
      MediaType("application", "vnd.sema", compressible = false, binary = true, fileExtensions = List("sema"))
    
    lazy val `vnd.sema`: MediaType = vndSema

    lazy val vndSemd: MediaType =
      MediaType("application", "vnd.semd", compressible = false, binary = true, fileExtensions = List("semd"))
    
    lazy val `vnd.semd`: MediaType = vndSemd

    lazy val vndSemf: MediaType =
      MediaType("application", "vnd.semf", compressible = false, binary = true, fileExtensions = List("semf"))
    
    lazy val `vnd.semf`: MediaType = vndSemf

    lazy val vndShadeSaveFile: MediaType =
      MediaType("application", "vnd.shade-save-file", compressible = false, binary = true)
    
    lazy val `vnd.shade-save-file`: MediaType = vndShadeSaveFile

    lazy val vndShanaInformedFormdata: MediaType =
      MediaType("application", "vnd.shana.informed.formdata", compressible = false, binary = true, fileExtensions = List("ifm"))
    
    lazy val `vnd.shana.informed.formdata`: MediaType = vndShanaInformedFormdata

    lazy val vndShanaInformedFormtemplate: MediaType =
      MediaType("application", "vnd.shana.informed.formtemplate", compressible = false, binary = true, fileExtensions = List("itp"))
    
    lazy val `vnd.shana.informed.formtemplate`: MediaType = vndShanaInformedFormtemplate

    lazy val vndShanaInformedInterchange: MediaType =
      MediaType("application", "vnd.shana.informed.interchange", compressible = false, binary = true, fileExtensions = List("iif"))
    
    lazy val `vnd.shana.informed.interchange`: MediaType = vndShanaInformedInterchange

    lazy val vndShanaInformedPackage: MediaType =
      MediaType("application", "vnd.shana.informed.package", compressible = false, binary = true, fileExtensions = List("ipk"))
    
    lazy val `vnd.shana.informed.package`: MediaType = vndShanaInformedPackage

    lazy val vndShootproofJson: MediaType =
      MediaType("application", "vnd.shootproof+json", compressible = true, binary = false)
    
    lazy val `vnd.shootproof+json`: MediaType = vndShootproofJson

    lazy val vndShopkickJson: MediaType =
      MediaType("application", "vnd.shopkick+json", compressible = true, binary = false)
    
    lazy val `vnd.shopkick+json`: MediaType = vndShopkickJson

    lazy val vndShp: MediaType =
      MediaType("application", "vnd.shp", compressible = false, binary = true)
    
    lazy val `vnd.shp`: MediaType = vndShp

    lazy val vndShx: MediaType =
      MediaType("application", "vnd.shx", compressible = false, binary = true)
    
    lazy val `vnd.shx`: MediaType = vndShx

    lazy val vndSigrokSession: MediaType =
      MediaType("application", "vnd.sigrok.session", compressible = false, binary = true)
    
    lazy val `vnd.sigrok.session`: MediaType = vndSigrokSession

    lazy val vndSimtechMindmapper: MediaType =
      MediaType("application", "vnd.simtech-mindmapper", compressible = false, binary = true, fileExtensions = List("twd", "twds"))
    
    lazy val `vnd.simtech-mindmapper`: MediaType = vndSimtechMindmapper

    lazy val vndSirenJson: MediaType =
      MediaType("application", "vnd.siren+json", compressible = true, binary = false)
    
    lazy val `vnd.siren+json`: MediaType = vndSirenJson

    lazy val vndSirtxVmv0: MediaType =
      MediaType("application", "vnd.sirtx.vmv0", compressible = false, binary = true)
    
    lazy val `vnd.sirtx.vmv0`: MediaType = vndSirtxVmv0

    lazy val vndSketchometry: MediaType =
      MediaType("application", "vnd.sketchometry", compressible = false, binary = true)
    
    lazy val `vnd.sketchometry`: MediaType = vndSketchometry

    lazy val vndSmaf: MediaType =
      MediaType("application", "vnd.smaf", compressible = false, binary = true, fileExtensions = List("mmf"))
    
    lazy val `vnd.smaf`: MediaType = vndSmaf

    lazy val vndSmartNotebook: MediaType =
      MediaType("application", "vnd.smart.notebook", compressible = false, binary = true)
    
    lazy val `vnd.smart.notebook`: MediaType = vndSmartNotebook

    lazy val vndSmartTeacher: MediaType =
      MediaType("application", "vnd.smart.teacher", compressible = false, binary = true, fileExtensions = List("teacher"))
    
    lazy val `vnd.smart.teacher`: MediaType = vndSmartTeacher

    lazy val vndSmintioPortalsArchive: MediaType =
      MediaType("application", "vnd.smintio.portals.archive", compressible = false, binary = true)
    
    lazy val `vnd.smintio.portals.archive`: MediaType = vndSmintioPortalsArchive

    lazy val vndSnesdevPageTable: MediaType =
      MediaType("application", "vnd.snesdev-page-table", compressible = false, binary = true)
    
    lazy val `vnd.snesdev-page-table`: MediaType = vndSnesdevPageTable

    lazy val vndSoftware602FillerFormXml: MediaType =
      MediaType("application", "vnd.software602.filler.form+xml", compressible = true, binary = true, fileExtensions = List("fo"))
    
    lazy val `vnd.software602.filler.form+xml`: MediaType = vndSoftware602FillerFormXml

    lazy val vndSoftware602FillerFormXmlZip: MediaType =
      MediaType("application", "vnd.software602.filler.form-xml-zip", compressible = false, binary = true)
    
    lazy val `vnd.software602.filler.form-xml-zip`: MediaType = vndSoftware602FillerFormXmlZip

    lazy val vndSolentSdkmXml: MediaType =
      MediaType("application", "vnd.solent.sdkm+xml", compressible = true, binary = true, fileExtensions = List("sdkm", "sdkd"))
    
    lazy val `vnd.solent.sdkm+xml`: MediaType = vndSolentSdkmXml

    lazy val vndSpotfireDxp: MediaType =
      MediaType("application", "vnd.spotfire.dxp", compressible = false, binary = true, fileExtensions = List("dxp"))
    
    lazy val `vnd.spotfire.dxp`: MediaType = vndSpotfireDxp

    lazy val vndSpotfireSfs: MediaType =
      MediaType("application", "vnd.spotfire.sfs", compressible = false, binary = true, fileExtensions = List("sfs"))
    
    lazy val `vnd.spotfire.sfs`: MediaType = vndSpotfireSfs

    lazy val vndSqlite3: MediaType =
      MediaType("application", "vnd.sqlite3", compressible = false, binary = true, fileExtensions = List("sqlite", "sqlite3"))
    
    lazy val `vnd.sqlite3`: MediaType = vndSqlite3

    lazy val vndSssCod: MediaType =
      MediaType("application", "vnd.sss-cod", compressible = false, binary = true)
    
    lazy val `vnd.sss-cod`: MediaType = vndSssCod

    lazy val vndSssDtf: MediaType =
      MediaType("application", "vnd.sss-dtf", compressible = false, binary = true)
    
    lazy val `vnd.sss-dtf`: MediaType = vndSssDtf

    lazy val vndSssNtf: MediaType =
      MediaType("application", "vnd.sss-ntf", compressible = false, binary = true)
    
    lazy val `vnd.sss-ntf`: MediaType = vndSssNtf

    lazy val vndStardivisionCalc: MediaType =
      MediaType("application", "vnd.stardivision.calc", compressible = false, binary = true, fileExtensions = List("sdc"))
    
    lazy val `vnd.stardivision.calc`: MediaType = vndStardivisionCalc

    lazy val vndStardivisionDraw: MediaType =
      MediaType("application", "vnd.stardivision.draw", compressible = false, binary = true, fileExtensions = List("sda"))
    
    lazy val `vnd.stardivision.draw`: MediaType = vndStardivisionDraw

    lazy val vndStardivisionImpress: MediaType =
      MediaType("application", "vnd.stardivision.impress", compressible = false, binary = true, fileExtensions = List("sdd"))
    
    lazy val `vnd.stardivision.impress`: MediaType = vndStardivisionImpress

    lazy val vndStardivisionMath: MediaType =
      MediaType("application", "vnd.stardivision.math", compressible = false, binary = true, fileExtensions = List("smf"))
    
    lazy val `vnd.stardivision.math`: MediaType = vndStardivisionMath

    lazy val vndStardivisionWriter: MediaType =
      MediaType("application", "vnd.stardivision.writer", compressible = false, binary = true, fileExtensions = List("sdw", "vor"))
    
    lazy val `vnd.stardivision.writer`: MediaType = vndStardivisionWriter

    lazy val vndStardivisionWriterGlobal: MediaType =
      MediaType("application", "vnd.stardivision.writer-global", compressible = false, binary = true, fileExtensions = List("sgl"))
    
    lazy val `vnd.stardivision.writer-global`: MediaType = vndStardivisionWriterGlobal

    lazy val vndStepmaniaPackage: MediaType =
      MediaType("application", "vnd.stepmania.package", compressible = false, binary = true, fileExtensions = List("smzip"))
    
    lazy val `vnd.stepmania.package`: MediaType = vndStepmaniaPackage

    lazy val vndStepmaniaStepchart: MediaType =
      MediaType("application", "vnd.stepmania.stepchart", compressible = false, binary = true, fileExtensions = List("sm"))
    
    lazy val `vnd.stepmania.stepchart`: MediaType = vndStepmaniaStepchart

    lazy val vndStreetStream: MediaType =
      MediaType("application", "vnd.street-stream", compressible = false, binary = true)
    
    lazy val `vnd.street-stream`: MediaType = vndStreetStream

    lazy val vndSunWadlXml: MediaType =
      MediaType("application", "vnd.sun.wadl+xml", compressible = true, binary = true, fileExtensions = List("wadl"))
    
    lazy val `vnd.sun.wadl+xml`: MediaType = vndSunWadlXml

    lazy val vndSunXmlCalc: MediaType =
      MediaType("application", "vnd.sun.xml.calc", compressible = false, binary = true, fileExtensions = List("sxc"))
    
    lazy val `vnd.sun.xml.calc`: MediaType = vndSunXmlCalc

    lazy val vndSunXmlCalcTemplate: MediaType =
      MediaType("application", "vnd.sun.xml.calc.template", compressible = false, binary = true, fileExtensions = List("stc"))
    
    lazy val `vnd.sun.xml.calc.template`: MediaType = vndSunXmlCalcTemplate

    lazy val vndSunXmlDraw: MediaType =
      MediaType("application", "vnd.sun.xml.draw", compressible = false, binary = true, fileExtensions = List("sxd"))
    
    lazy val `vnd.sun.xml.draw`: MediaType = vndSunXmlDraw

    lazy val vndSunXmlDrawTemplate: MediaType =
      MediaType("application", "vnd.sun.xml.draw.template", compressible = false, binary = true, fileExtensions = List("std"))
    
    lazy val `vnd.sun.xml.draw.template`: MediaType = vndSunXmlDrawTemplate

    lazy val vndSunXmlImpress: MediaType =
      MediaType("application", "vnd.sun.xml.impress", compressible = false, binary = true, fileExtensions = List("sxi"))
    
    lazy val `vnd.sun.xml.impress`: MediaType = vndSunXmlImpress

    lazy val vndSunXmlImpressTemplate: MediaType =
      MediaType("application", "vnd.sun.xml.impress.template", compressible = false, binary = true, fileExtensions = List("sti"))
    
    lazy val `vnd.sun.xml.impress.template`: MediaType = vndSunXmlImpressTemplate

    lazy val vndSunXmlMath: MediaType =
      MediaType("application", "vnd.sun.xml.math", compressible = false, binary = true, fileExtensions = List("sxm"))
    
    lazy val `vnd.sun.xml.math`: MediaType = vndSunXmlMath

    lazy val vndSunXmlWriter: MediaType =
      MediaType("application", "vnd.sun.xml.writer", compressible = false, binary = true, fileExtensions = List("sxw"))
    
    lazy val `vnd.sun.xml.writer`: MediaType = vndSunXmlWriter

    lazy val vndSunXmlWriterGlobal: MediaType =
      MediaType("application", "vnd.sun.xml.writer.global", compressible = false, binary = true, fileExtensions = List("sxg"))
    
    lazy val `vnd.sun.xml.writer.global`: MediaType = vndSunXmlWriterGlobal

    lazy val vndSunXmlWriterTemplate: MediaType =
      MediaType("application", "vnd.sun.xml.writer.template", compressible = false, binary = true, fileExtensions = List("stw"))
    
    lazy val `vnd.sun.xml.writer.template`: MediaType = vndSunXmlWriterTemplate

    lazy val vndSuperfileSuper: MediaType =
      MediaType("application", "vnd.superfile.super", compressible = false, binary = true)
    
    lazy val `vnd.superfile.super`: MediaType = vndSuperfileSuper

    lazy val vndSusCalendar: MediaType =
      MediaType("application", "vnd.sus-calendar", compressible = false, binary = true, fileExtensions = List("sus", "susp"))
    
    lazy val `vnd.sus-calendar`: MediaType = vndSusCalendar

    lazy val vndSvd: MediaType =
      MediaType("application", "vnd.svd", compressible = false, binary = true, fileExtensions = List("svd"))
    
    lazy val `vnd.svd`: MediaType = vndSvd

    lazy val vndSwiftviewIcs: MediaType =
      MediaType("application", "vnd.swiftview-ics", compressible = false, binary = true)
    
    lazy val `vnd.swiftview-ics`: MediaType = vndSwiftviewIcs

    lazy val vndSybylMol2: MediaType =
      MediaType("application", "vnd.sybyl.mol2", compressible = false, binary = true)
    
    lazy val `vnd.sybyl.mol2`: MediaType = vndSybylMol2

    lazy val vndSycleXml: MediaType =
      MediaType("application", "vnd.sycle+xml", compressible = true, binary = true)
    
    lazy val `vnd.sycle+xml`: MediaType = vndSycleXml

    lazy val vndSyftJson: MediaType =
      MediaType("application", "vnd.syft+json", compressible = true, binary = false)
    
    lazy val `vnd.syft+json`: MediaType = vndSyftJson

    lazy val vndSymbianInstall: MediaType =
      MediaType("application", "vnd.symbian.install", compressible = false, binary = true, fileExtensions = List("sis", "sisx"))
    
    lazy val `vnd.symbian.install`: MediaType = vndSymbianInstall

    lazy val vndSyncmlXml: MediaType =
      MediaType("application", "vnd.syncml+xml", compressible = true, binary = true, fileExtensions = List("xsm"))
    
    lazy val `vnd.syncml+xml`: MediaType = vndSyncmlXml

    lazy val vndSyncmlDmWbxml: MediaType =
      MediaType("application", "vnd.syncml.dm+wbxml", compressible = false, binary = true, fileExtensions = List("bdm"))
    
    lazy val `vnd.syncml.dm+wbxml`: MediaType = vndSyncmlDmWbxml

    lazy val vndSyncmlDmXml: MediaType =
      MediaType("application", "vnd.syncml.dm+xml", compressible = true, binary = true, fileExtensions = List("xdm"))
    
    lazy val `vnd.syncml.dm+xml`: MediaType = vndSyncmlDmXml

    lazy val vndSyncmlDmNotification: MediaType =
      MediaType("application", "vnd.syncml.dm.notification", compressible = false, binary = true)
    
    lazy val `vnd.syncml.dm.notification`: MediaType = vndSyncmlDmNotification

    lazy val vndSyncmlDmddfWbxml: MediaType =
      MediaType("application", "vnd.syncml.dmddf+wbxml", compressible = false, binary = true)
    
    lazy val `vnd.syncml.dmddf+wbxml`: MediaType = vndSyncmlDmddfWbxml

    lazy val vndSyncmlDmddfXml: MediaType =
      MediaType("application", "vnd.syncml.dmddf+xml", compressible = true, binary = true, fileExtensions = List("ddf"))
    
    lazy val `vnd.syncml.dmddf+xml`: MediaType = vndSyncmlDmddfXml

    lazy val vndSyncmlDmtndsWbxml: MediaType =
      MediaType("application", "vnd.syncml.dmtnds+wbxml", compressible = false, binary = true)
    
    lazy val `vnd.syncml.dmtnds+wbxml`: MediaType = vndSyncmlDmtndsWbxml

    lazy val vndSyncmlDmtndsXml: MediaType =
      MediaType("application", "vnd.syncml.dmtnds+xml", compressible = true, binary = true)
    
    lazy val `vnd.syncml.dmtnds+xml`: MediaType = vndSyncmlDmtndsXml

    lazy val vndSyncmlDsNotification: MediaType =
      MediaType("application", "vnd.syncml.ds.notification", compressible = false, binary = true)
    
    lazy val `vnd.syncml.ds.notification`: MediaType = vndSyncmlDsNotification

    lazy val vndTableschemaJson: MediaType =
      MediaType("application", "vnd.tableschema+json", compressible = true, binary = false)
    
    lazy val `vnd.tableschema+json`: MediaType = vndTableschemaJson

    lazy val vndTaoIntentModuleArchive: MediaType =
      MediaType("application", "vnd.tao.intent-module-archive", compressible = false, binary = true, fileExtensions = List("tao"))
    
    lazy val `vnd.tao.intent-module-archive`: MediaType = vndTaoIntentModuleArchive

    lazy val vndTcpdumpPcap: MediaType =
      MediaType("application", "vnd.tcpdump.pcap", compressible = false, binary = true, fileExtensions = List("pcap", "cap", "dmp"))
    
    lazy val `vnd.tcpdump.pcap`: MediaType = vndTcpdumpPcap

    lazy val vndThinkCellPpttcJson: MediaType =
      MediaType("application", "vnd.think-cell.ppttc+json", compressible = true, binary = false)
    
    lazy val `vnd.think-cell.ppttc+json`: MediaType = vndThinkCellPpttcJson

    lazy val vndTmdMediaflexApiXml: MediaType =
      MediaType("application", "vnd.tmd.mediaflex.api+xml", compressible = true, binary = true)
    
    lazy val `vnd.tmd.mediaflex.api+xml`: MediaType = vndTmdMediaflexApiXml

    lazy val vndTml: MediaType =
      MediaType("application", "vnd.tml", compressible = false, binary = true)
    
    lazy val `vnd.tml`: MediaType = vndTml

    lazy val vndTmobileLivetv: MediaType =
      MediaType("application", "vnd.tmobile-livetv", compressible = false, binary = true, fileExtensions = List("tmo"))
    
    lazy val `vnd.tmobile-livetv`: MediaType = vndTmobileLivetv

    lazy val vndTriOnesource: MediaType =
      MediaType("application", "vnd.tri.onesource", compressible = false, binary = true)
    
    lazy val `vnd.tri.onesource`: MediaType = vndTriOnesource

    lazy val vndTridTpt: MediaType =
      MediaType("application", "vnd.trid.tpt", compressible = false, binary = true, fileExtensions = List("tpt"))
    
    lazy val `vnd.trid.tpt`: MediaType = vndTridTpt

    lazy val vndTriscapeMxs: MediaType =
      MediaType("application", "vnd.triscape.mxs", compressible = false, binary = true, fileExtensions = List("mxs"))
    
    lazy val `vnd.triscape.mxs`: MediaType = vndTriscapeMxs

    lazy val vndTrueapp: MediaType =
      MediaType("application", "vnd.trueapp", compressible = false, binary = true, fileExtensions = List("tra"))
    
    lazy val `vnd.trueapp`: MediaType = vndTrueapp

    lazy val vndTruedoc: MediaType =
      MediaType("application", "vnd.truedoc", compressible = false, binary = true)
    
    lazy val `vnd.truedoc`: MediaType = vndTruedoc

    lazy val vndUbisoftWebplayer: MediaType =
      MediaType("application", "vnd.ubisoft.webplayer", compressible = false, binary = true)
    
    lazy val `vnd.ubisoft.webplayer`: MediaType = vndUbisoftWebplayer

    lazy val vndUfdl: MediaType =
      MediaType("application", "vnd.ufdl", compressible = false, binary = true, fileExtensions = List("ufd", "ufdl"))
    
    lazy val `vnd.ufdl`: MediaType = vndUfdl

    lazy val vndUicDosipasV1: MediaType =
      MediaType("application", "vnd.uic.dosipas.v1", compressible = false, binary = true)
    
    lazy val `vnd.uic.dosipas.v1`: MediaType = vndUicDosipasV1

    lazy val vndUicDosipasV2: MediaType =
      MediaType("application", "vnd.uic.dosipas.v2", compressible = false, binary = true)
    
    lazy val `vnd.uic.dosipas.v2`: MediaType = vndUicDosipasV2

    lazy val vndUicOsdmJson: MediaType =
      MediaType("application", "vnd.uic.osdm+json", compressible = true, binary = false)
    
    lazy val `vnd.uic.osdm+json`: MediaType = vndUicOsdmJson

    lazy val vndUicTlbFcb: MediaType =
      MediaType("application", "vnd.uic.tlb-fcb", compressible = false, binary = true)
    
    lazy val `vnd.uic.tlb-fcb`: MediaType = vndUicTlbFcb

    lazy val vndUiqTheme: MediaType =
      MediaType("application", "vnd.uiq.theme", compressible = false, binary = true, fileExtensions = List("utz"))
    
    lazy val `vnd.uiq.theme`: MediaType = vndUiqTheme

    lazy val vndUmajin: MediaType =
      MediaType("application", "vnd.umajin", compressible = false, binary = true, fileExtensions = List("umj"))
    
    lazy val `vnd.umajin`: MediaType = vndUmajin

    lazy val vndUnity: MediaType =
      MediaType("application", "vnd.unity", compressible = false, binary = true, fileExtensions = List("unityweb"))
    
    lazy val `vnd.unity`: MediaType = vndUnity

    lazy val vndUomlXml: MediaType =
      MediaType("application", "vnd.uoml+xml", compressible = true, binary = true, fileExtensions = List("uoml", "uo"))
    
    lazy val `vnd.uoml+xml`: MediaType = vndUomlXml

    lazy val vndUplanetAlert: MediaType =
      MediaType("application", "vnd.uplanet.alert", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.alert`: MediaType = vndUplanetAlert

    lazy val vndUplanetAlertWbxml: MediaType =
      MediaType("application", "vnd.uplanet.alert-wbxml", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.alert-wbxml`: MediaType = vndUplanetAlertWbxml

    lazy val vndUplanetBearerChoice: MediaType =
      MediaType("application", "vnd.uplanet.bearer-choice", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.bearer-choice`: MediaType = vndUplanetBearerChoice

    lazy val vndUplanetBearerChoiceWbxml: MediaType =
      MediaType("application", "vnd.uplanet.bearer-choice-wbxml", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.bearer-choice-wbxml`: MediaType = vndUplanetBearerChoiceWbxml

    lazy val vndUplanetCacheop: MediaType =
      MediaType("application", "vnd.uplanet.cacheop", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.cacheop`: MediaType = vndUplanetCacheop

    lazy val vndUplanetCacheopWbxml: MediaType =
      MediaType("application", "vnd.uplanet.cacheop-wbxml", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.cacheop-wbxml`: MediaType = vndUplanetCacheopWbxml

    lazy val vndUplanetChannel: MediaType =
      MediaType("application", "vnd.uplanet.channel", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.channel`: MediaType = vndUplanetChannel

    lazy val vndUplanetChannelWbxml: MediaType =
      MediaType("application", "vnd.uplanet.channel-wbxml", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.channel-wbxml`: MediaType = vndUplanetChannelWbxml

    lazy val vndUplanetList: MediaType =
      MediaType("application", "vnd.uplanet.list", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.list`: MediaType = vndUplanetList

    lazy val vndUplanetListWbxml: MediaType =
      MediaType("application", "vnd.uplanet.list-wbxml", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.list-wbxml`: MediaType = vndUplanetListWbxml

    lazy val vndUplanetListcmd: MediaType =
      MediaType("application", "vnd.uplanet.listcmd", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.listcmd`: MediaType = vndUplanetListcmd

    lazy val vndUplanetListcmdWbxml: MediaType =
      MediaType("application", "vnd.uplanet.listcmd-wbxml", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.listcmd-wbxml`: MediaType = vndUplanetListcmdWbxml

    lazy val vndUplanetSignal: MediaType =
      MediaType("application", "vnd.uplanet.signal", compressible = false, binary = true)
    
    lazy val `vnd.uplanet.signal`: MediaType = vndUplanetSignal

    lazy val vndUriMap: MediaType =
      MediaType("application", "vnd.uri-map", compressible = false, binary = true)
    
    lazy val `vnd.uri-map`: MediaType = vndUriMap

    lazy val vndValveSourceMaterial: MediaType =
      MediaType("application", "vnd.valve.source.material", compressible = false, binary = true)
    
    lazy val `vnd.valve.source.material`: MediaType = vndValveSourceMaterial

    lazy val vndVcx: MediaType =
      MediaType("application", "vnd.vcx", compressible = false, binary = true, fileExtensions = List("vcx"))
    
    lazy val `vnd.vcx`: MediaType = vndVcx

    lazy val vndVdStudy: MediaType =
      MediaType("application", "vnd.vd-study", compressible = false, binary = true)
    
    lazy val `vnd.vd-study`: MediaType = vndVdStudy

    lazy val vndVectorworks: MediaType =
      MediaType("application", "vnd.vectorworks", compressible = false, binary = true)
    
    lazy val `vnd.vectorworks`: MediaType = vndVectorworks

    lazy val vndVelJson: MediaType =
      MediaType("application", "vnd.vel+json", compressible = true, binary = false)
    
    lazy val `vnd.vel+json`: MediaType = vndVelJson

    lazy val vndVeraisonTsmReportCbor: MediaType =
      MediaType("application", "vnd.veraison.tsm-report+cbor", compressible = false, binary = true)
    
    lazy val `vnd.veraison.tsm-report+cbor`: MediaType = vndVeraisonTsmReportCbor

    lazy val vndVeraisonTsmReportJson: MediaType =
      MediaType("application", "vnd.veraison.tsm-report+json", compressible = true, binary = false)
    
    lazy val `vnd.veraison.tsm-report+json`: MediaType = vndVeraisonTsmReportJson

    lazy val vndVerifierAttestationJwt: MediaType =
      MediaType("application", "vnd.verifier-attestation+jwt", compressible = false, binary = true)
    
    lazy val `vnd.verifier-attestation+jwt`: MediaType = vndVerifierAttestationJwt

    lazy val vndVerimatrixVcas: MediaType =
      MediaType("application", "vnd.verimatrix.vcas", compressible = false, binary = true)
    
    lazy val `vnd.verimatrix.vcas`: MediaType = vndVerimatrixVcas

    lazy val vndVeritoneAionJson: MediaType =
      MediaType("application", "vnd.veritone.aion+json", compressible = true, binary = false)
    
    lazy val `vnd.veritone.aion+json`: MediaType = vndVeritoneAionJson

    lazy val vndVeryantThin: MediaType =
      MediaType("application", "vnd.veryant.thin", compressible = false, binary = true)
    
    lazy val `vnd.veryant.thin`: MediaType = vndVeryantThin

    lazy val vndVesEncrypted: MediaType =
      MediaType("application", "vnd.ves.encrypted", compressible = false, binary = true)
    
    lazy val `vnd.ves.encrypted`: MediaType = vndVesEncrypted

    lazy val vndVidsoftVidconference: MediaType =
      MediaType("application", "vnd.vidsoft.vidconference", compressible = false, binary = true)
    
    lazy val `vnd.vidsoft.vidconference`: MediaType = vndVidsoftVidconference

    lazy val vndVisio: MediaType =
      MediaType("application", "vnd.visio", compressible = false, binary = true, fileExtensions = List("vsd", "vst", "vss", "vsw", "vsdx", "vtx"))
    
    lazy val `vnd.visio`: MediaType = vndVisio

    lazy val vndVisionary: MediaType =
      MediaType("application", "vnd.visionary", compressible = false, binary = true, fileExtensions = List("vis"))
    
    lazy val `vnd.visionary`: MediaType = vndVisionary

    lazy val vndVividenceScriptfile: MediaType =
      MediaType("application", "vnd.vividence.scriptfile", compressible = false, binary = true)
    
    lazy val `vnd.vividence.scriptfile`: MediaType = vndVividenceScriptfile

    lazy val vndVocalshaperVsp4: MediaType =
      MediaType("application", "vnd.vocalshaper.vsp4", compressible = false, binary = true)
    
    lazy val `vnd.vocalshaper.vsp4`: MediaType = vndVocalshaperVsp4

    lazy val vndVsf: MediaType =
      MediaType("application", "vnd.vsf", compressible = false, binary = true, fileExtensions = List("vsf"))
    
    lazy val `vnd.vsf`: MediaType = vndVsf

    lazy val vndVuq: MediaType =
      MediaType("application", "vnd.vuq", compressible = false, binary = true)
    
    lazy val `vnd.vuq`: MediaType = vndVuq

    lazy val vndWantverse: MediaType =
      MediaType("application", "vnd.wantverse", compressible = false, binary = true)
    
    lazy val `vnd.wantverse`: MediaType = vndWantverse

    lazy val vndWapSic: MediaType =
      MediaType("application", "vnd.wap.sic", compressible = false, binary = true)
    
    lazy val `vnd.wap.sic`: MediaType = vndWapSic

    lazy val vndWapSlc: MediaType =
      MediaType("application", "vnd.wap.slc", compressible = false, binary = true)
    
    lazy val `vnd.wap.slc`: MediaType = vndWapSlc

    lazy val vndWapWbxml: MediaType =
      MediaType("application", "vnd.wap.wbxml", compressible = false, binary = true, fileExtensions = List("wbxml"))
    
    lazy val `vnd.wap.wbxml`: MediaType = vndWapWbxml

    lazy val vndWapWmlc: MediaType =
      MediaType("application", "vnd.wap.wmlc", compressible = false, binary = true, fileExtensions = List("wmlc"))
    
    lazy val `vnd.wap.wmlc`: MediaType = vndWapWmlc

    lazy val vndWapWmlscriptc: MediaType =
      MediaType("application", "vnd.wap.wmlscriptc", compressible = false, binary = true, fileExtensions = List("wmlsc"))
    
    lazy val `vnd.wap.wmlscriptc`: MediaType = vndWapWmlscriptc

    lazy val vndWasmflowWafl: MediaType =
      MediaType("application", "vnd.wasmflow.wafl", compressible = false, binary = true)
    
    lazy val `vnd.wasmflow.wafl`: MediaType = vndWasmflowWafl

    lazy val vndWebturbo: MediaType =
      MediaType("application", "vnd.webturbo", compressible = false, binary = true, fileExtensions = List("wtb"))
    
    lazy val `vnd.webturbo`: MediaType = vndWebturbo

    lazy val vndWfaDpp: MediaType =
      MediaType("application", "vnd.wfa.dpp", compressible = false, binary = true)
    
    lazy val `vnd.wfa.dpp`: MediaType = vndWfaDpp

    lazy val vndWfaP2p: MediaType =
      MediaType("application", "vnd.wfa.p2p", compressible = false, binary = true)
    
    lazy val `vnd.wfa.p2p`: MediaType = vndWfaP2p

    lazy val vndWfaWsc: MediaType =
      MediaType("application", "vnd.wfa.wsc", compressible = false, binary = true)
    
    lazy val `vnd.wfa.wsc`: MediaType = vndWfaWsc

    lazy val vndWindowsDevicepairing: MediaType =
      MediaType("application", "vnd.windows.devicepairing", compressible = false, binary = true)
    
    lazy val `vnd.windows.devicepairing`: MediaType = vndWindowsDevicepairing

    lazy val vndWmap: MediaType =
      MediaType("application", "vnd.wmap", compressible = false, binary = true)
    
    lazy val `vnd.wmap`: MediaType = vndWmap

    lazy val vndWmc: MediaType =
      MediaType("application", "vnd.wmc", compressible = false, binary = true)
    
    lazy val `vnd.wmc`: MediaType = vndWmc

    lazy val vndWmfBootstrap: MediaType =
      MediaType("application", "vnd.wmf.bootstrap", compressible = false, binary = true)
    
    lazy val `vnd.wmf.bootstrap`: MediaType = vndWmfBootstrap

    lazy val vndWolframMathematica: MediaType =
      MediaType("application", "vnd.wolfram.mathematica", compressible = false, binary = true)
    
    lazy val `vnd.wolfram.mathematica`: MediaType = vndWolframMathematica

    lazy val vndWolframMathematicaPackage: MediaType =
      MediaType("application", "vnd.wolfram.mathematica.package", compressible = false, binary = true)
    
    lazy val `vnd.wolfram.mathematica.package`: MediaType = vndWolframMathematicaPackage

    lazy val vndWolframPlayer: MediaType =
      MediaType("application", "vnd.wolfram.player", compressible = false, binary = true, fileExtensions = List("nbp"))
    
    lazy val `vnd.wolfram.player`: MediaType = vndWolframPlayer

    lazy val vndWordlift: MediaType =
      MediaType("application", "vnd.wordlift", compressible = false, binary = true)
    
    lazy val `vnd.wordlift`: MediaType = vndWordlift

    lazy val vndWordperfect: MediaType =
      MediaType("application", "vnd.wordperfect", compressible = false, binary = true, fileExtensions = List("wpd"))
    
    lazy val `vnd.wordperfect`: MediaType = vndWordperfect

    lazy val vndWqd: MediaType =
      MediaType("application", "vnd.wqd", compressible = false, binary = true, fileExtensions = List("wqd"))
    
    lazy val `vnd.wqd`: MediaType = vndWqd

    lazy val vndWrqHp3000Labelled: MediaType =
      MediaType("application", "vnd.wrq-hp3000-labelled", compressible = false, binary = true)
    
    lazy val `vnd.wrq-hp3000-labelled`: MediaType = vndWrqHp3000Labelled

    lazy val vndWtStf: MediaType =
      MediaType("application", "vnd.wt.stf", compressible = false, binary = true, fileExtensions = List("stf"))
    
    lazy val `vnd.wt.stf`: MediaType = vndWtStf

    lazy val vndWvCspWbxml: MediaType =
      MediaType("application", "vnd.wv.csp+wbxml", compressible = false, binary = true)
    
    lazy val `vnd.wv.csp+wbxml`: MediaType = vndWvCspWbxml

    lazy val vndWvCspXml: MediaType =
      MediaType("application", "vnd.wv.csp+xml", compressible = true, binary = true)
    
    lazy val `vnd.wv.csp+xml`: MediaType = vndWvCspXml

    lazy val vndWvSspXml: MediaType =
      MediaType("application", "vnd.wv.ssp+xml", compressible = true, binary = true)
    
    lazy val `vnd.wv.ssp+xml`: MediaType = vndWvSspXml

    lazy val vndXacmlJson: MediaType =
      MediaType("application", "vnd.xacml+json", compressible = true, binary = false)
    
    lazy val `vnd.xacml+json`: MediaType = vndXacmlJson

    lazy val vndXara: MediaType =
      MediaType("application", "vnd.xara", compressible = false, binary = true, fileExtensions = List("xar"))
    
    lazy val `vnd.xara`: MediaType = vndXara

    lazy val vndXarinCpj: MediaType =
      MediaType("application", "vnd.xarin.cpj", compressible = false, binary = true)
    
    lazy val `vnd.xarin.cpj`: MediaType = vndXarinCpj

    lazy val vndXcdn: MediaType =
      MediaType("application", "vnd.xcdn", compressible = false, binary = true)
    
    lazy val `vnd.xcdn`: MediaType = vndXcdn

    lazy val vndXecretsEncrypted: MediaType =
      MediaType("application", "vnd.xecrets-encrypted", compressible = false, binary = true)
    
    lazy val `vnd.xecrets-encrypted`: MediaType = vndXecretsEncrypted

    lazy val vndXfdl: MediaType =
      MediaType("application", "vnd.xfdl", compressible = false, binary = true, fileExtensions = List("xfdl"))
    
    lazy val `vnd.xfdl`: MediaType = vndXfdl

    lazy val vndXfdlWebform: MediaType =
      MediaType("application", "vnd.xfdl.webform", compressible = false, binary = true)
    
    lazy val `vnd.xfdl.webform`: MediaType = vndXfdlWebform

    lazy val vndXmiXml: MediaType =
      MediaType("application", "vnd.xmi+xml", compressible = true, binary = true)
    
    lazy val `vnd.xmi+xml`: MediaType = vndXmiXml

    lazy val vndXmpieCpkg: MediaType =
      MediaType("application", "vnd.xmpie.cpkg", compressible = false, binary = true)
    
    lazy val `vnd.xmpie.cpkg`: MediaType = vndXmpieCpkg

    lazy val vndXmpieDpkg: MediaType =
      MediaType("application", "vnd.xmpie.dpkg", compressible = false, binary = true)
    
    lazy val `vnd.xmpie.dpkg`: MediaType = vndXmpieDpkg

    lazy val vndXmpiePlan: MediaType =
      MediaType("application", "vnd.xmpie.plan", compressible = false, binary = true)
    
    lazy val `vnd.xmpie.plan`: MediaType = vndXmpiePlan

    lazy val vndXmpiePpkg: MediaType =
      MediaType("application", "vnd.xmpie.ppkg", compressible = false, binary = true)
    
    lazy val `vnd.xmpie.ppkg`: MediaType = vndXmpiePpkg

    lazy val vndXmpieXlim: MediaType =
      MediaType("application", "vnd.xmpie.xlim", compressible = false, binary = true)
    
    lazy val `vnd.xmpie.xlim`: MediaType = vndXmpieXlim

    lazy val vndYamahaHvDic: MediaType =
      MediaType("application", "vnd.yamaha.hv-dic", compressible = false, binary = true, fileExtensions = List("hvd"))
    
    lazy val `vnd.yamaha.hv-dic`: MediaType = vndYamahaHvDic

    lazy val vndYamahaHvScript: MediaType =
      MediaType("application", "vnd.yamaha.hv-script", compressible = false, binary = true, fileExtensions = List("hvs"))
    
    lazy val `vnd.yamaha.hv-script`: MediaType = vndYamahaHvScript

    lazy val vndYamahaHvVoice: MediaType =
      MediaType("application", "vnd.yamaha.hv-voice", compressible = false, binary = true, fileExtensions = List("hvp"))
    
    lazy val `vnd.yamaha.hv-voice`: MediaType = vndYamahaHvVoice

    lazy val vndYamahaOpenscoreformat: MediaType =
      MediaType("application", "vnd.yamaha.openscoreformat", compressible = false, binary = true, fileExtensions = List("osf"))
    
    lazy val `vnd.yamaha.openscoreformat`: MediaType = vndYamahaOpenscoreformat

    lazy val vndYamahaOpenscoreformatOsfpvgXml: MediaType =
      MediaType("application", "vnd.yamaha.openscoreformat.osfpvg+xml", compressible = true, binary = true, fileExtensions = List("osfpvg"))
    
    lazy val `vnd.yamaha.openscoreformat.osfpvg+xml`: MediaType = vndYamahaOpenscoreformatOsfpvgXml

    lazy val vndYamahaRemoteSetup: MediaType =
      MediaType("application", "vnd.yamaha.remote-setup", compressible = false, binary = true)
    
    lazy val `vnd.yamaha.remote-setup`: MediaType = vndYamahaRemoteSetup

    lazy val vndYamahaSmafAudio: MediaType =
      MediaType("application", "vnd.yamaha.smaf-audio", compressible = false, binary = true, fileExtensions = List("saf"))
    
    lazy val `vnd.yamaha.smaf-audio`: MediaType = vndYamahaSmafAudio

    lazy val vndYamahaSmafPhrase: MediaType =
      MediaType("application", "vnd.yamaha.smaf-phrase", compressible = false, binary = true, fileExtensions = List("spf"))
    
    lazy val `vnd.yamaha.smaf-phrase`: MediaType = vndYamahaSmafPhrase

    lazy val vndYamahaThroughNgn: MediaType =
      MediaType("application", "vnd.yamaha.through-ngn", compressible = false, binary = true)
    
    lazy val `vnd.yamaha.through-ngn`: MediaType = vndYamahaThroughNgn

    lazy val vndYamahaTunnelUdpencap: MediaType =
      MediaType("application", "vnd.yamaha.tunnel-udpencap", compressible = false, binary = true)
    
    lazy val `vnd.yamaha.tunnel-udpencap`: MediaType = vndYamahaTunnelUdpencap

    lazy val vndYaoweme: MediaType =
      MediaType("application", "vnd.yaoweme", compressible = false, binary = true)
    
    lazy val `vnd.yaoweme`: MediaType = vndYaoweme

    lazy val vndYellowriverCustomMenu: MediaType =
      MediaType("application", "vnd.yellowriver-custom-menu", compressible = false, binary = true, fileExtensions = List("cmp"))
    
    lazy val `vnd.yellowriver-custom-menu`: MediaType = vndYellowriverCustomMenu

    lazy val vndZohoPresentationShow: MediaType =
      MediaType("application", "vnd.zoho-presentation.show", compressible = false, binary = true)
    
    lazy val `vnd.zoho-presentation.show`: MediaType = vndZohoPresentationShow

    lazy val vndZul: MediaType =
      MediaType("application", "vnd.zul", compressible = false, binary = true, fileExtensions = List("zir", "zirz"))
    
    lazy val `vnd.zul`: MediaType = vndZul

    lazy val vndZzazzDeckXml: MediaType =
      MediaType("application", "vnd.zzazz.deck+xml", compressible = true, binary = true, fileExtensions = List("zaz"))
    
    lazy val `vnd.zzazz.deck+xml`: MediaType = vndZzazzDeckXml

    lazy val voicexmlXml: MediaType =
      MediaType("application", "voicexml+xml", compressible = true, binary = true, fileExtensions = List("vxml"))
    
    lazy val `voicexml+xml`: MediaType = voicexmlXml

    lazy val voucherCmsJson: MediaType =
      MediaType("application", "voucher-cms+json", compressible = true, binary = false)
    
    lazy val `voucher-cms+json`: MediaType = voucherCmsJson

    lazy val voucherJwsJson: MediaType =
      MediaType("application", "voucher-jws+json", compressible = true, binary = false)
    
    lazy val `voucher-jws+json`: MediaType = voucherJwsJson

    lazy val vp: MediaType =
      MediaType("application", "vp", compressible = false, binary = true)

    lazy val vpCose: MediaType =
      MediaType("application", "vp+cose", compressible = false, binary = true)
    
    lazy val `vp+cose`: MediaType = vpCose

    lazy val vpJwt: MediaType =
      MediaType("application", "vp+jwt", compressible = false, binary = true)
    
    lazy val `vp+jwt`: MediaType = vpJwt

    lazy val vpSdJwt: MediaType =
      MediaType("application", "vp+sd-jwt", compressible = false, binary = true)
    
    lazy val `vp+sd-jwt`: MediaType = vpSdJwt

    lazy val vqRtcpxr: MediaType =
      MediaType("application", "vq-rtcpxr", compressible = false, binary = true)
    
    lazy val `vq-rtcpxr`: MediaType = vqRtcpxr

    lazy val wasm: MediaType =
      MediaType("application", "wasm", compressible = true, binary = true, fileExtensions = List("wasm"))

    lazy val watcherinfoXml: MediaType =
      MediaType("application", "watcherinfo+xml", compressible = true, binary = true, fileExtensions = List("wif"))
    
    lazy val `watcherinfo+xml`: MediaType = watcherinfoXml

    lazy val webpushOptionsJson: MediaType =
      MediaType("application", "webpush-options+json", compressible = true, binary = false)
    
    lazy val `webpush-options+json`: MediaType = webpushOptionsJson

    lazy val whoisppQuery: MediaType =
      MediaType("application", "whoispp-query", compressible = false, binary = true)
    
    lazy val `whoispp-query`: MediaType = whoisppQuery

    lazy val whoisppResponse: MediaType =
      MediaType("application", "whoispp-response", compressible = false, binary = true)
    
    lazy val `whoispp-response`: MediaType = whoisppResponse

    lazy val widget: MediaType =
      MediaType("application", "widget", compressible = false, binary = true, fileExtensions = List("wgt"))

    lazy val winhlp: MediaType =
      MediaType("application", "winhlp", compressible = false, binary = true, fileExtensions = List("hlp"))

    lazy val wita: MediaType =
      MediaType("application", "wita", compressible = false, binary = true)

    lazy val wordperfect51: MediaType =
      MediaType("application", "wordperfect5.1", compressible = false, binary = true)
    
    lazy val `wordperfect5.1`: MediaType = wordperfect51

    lazy val wsdlXml: MediaType =
      MediaType("application", "wsdl+xml", compressible = true, binary = true, fileExtensions = List("wsdl"))
    
    lazy val `wsdl+xml`: MediaType = wsdlXml

    lazy val wspolicyXml: MediaType =
      MediaType("application", "wspolicy+xml", compressible = true, binary = true, fileExtensions = List("wspolicy"))
    
    lazy val `wspolicy+xml`: MediaType = wspolicyXml

    lazy val x7zCompressed: MediaType =
      MediaType("application", "x-7z-compressed", compressible = false, binary = true, fileExtensions = List("7z"))
    
    lazy val `x-7z-compressed`: MediaType = x7zCompressed

    lazy val xAbiword: MediaType =
      MediaType("application", "x-abiword", compressible = false, binary = true, fileExtensions = List("abw"))
    
    lazy val `x-abiword`: MediaType = xAbiword

    lazy val xAceCompressed: MediaType =
      MediaType("application", "x-ace-compressed", compressible = false, binary = true, fileExtensions = List("ace"))
    
    lazy val `x-ace-compressed`: MediaType = xAceCompressed

    lazy val xAmf: MediaType =
      MediaType("application", "x-amf", compressible = false, binary = true)
    
    lazy val `x-amf`: MediaType = xAmf

    lazy val xAppleDiskimage: MediaType =
      MediaType("application", "x-apple-diskimage", compressible = false, binary = true, fileExtensions = List("dmg"))
    
    lazy val `x-apple-diskimage`: MediaType = xAppleDiskimage

    lazy val xArj: MediaType =
      MediaType("application", "x-arj", compressible = false, binary = true, fileExtensions = List("arj"))
    
    lazy val `x-arj`: MediaType = xArj

    lazy val xAuthorwareBin: MediaType =
      MediaType("application", "x-authorware-bin", compressible = false, binary = true, fileExtensions = List("aab", "x32", "u32", "vox"))
    
    lazy val `x-authorware-bin`: MediaType = xAuthorwareBin

    lazy val xAuthorwareMap: MediaType =
      MediaType("application", "x-authorware-map", compressible = false, binary = true, fileExtensions = List("aam"))
    
    lazy val `x-authorware-map`: MediaType = xAuthorwareMap

    lazy val xAuthorwareSeg: MediaType =
      MediaType("application", "x-authorware-seg", compressible = false, binary = true, fileExtensions = List("aas"))
    
    lazy val `x-authorware-seg`: MediaType = xAuthorwareSeg

    lazy val xBcpio: MediaType =
      MediaType("application", "x-bcpio", compressible = false, binary = true, fileExtensions = List("bcpio"))
    
    lazy val `x-bcpio`: MediaType = xBcpio

    lazy val xBdoc: MediaType =
      MediaType("application", "x-bdoc", compressible = false, binary = true, fileExtensions = List("bdoc"))
    
    lazy val `x-bdoc`: MediaType = xBdoc

    lazy val xBittorrent: MediaType =
      MediaType("application", "x-bittorrent", compressible = false, binary = true, fileExtensions = List("torrent"))
    
    lazy val `x-bittorrent`: MediaType = xBittorrent

    lazy val xBlender: MediaType =
      MediaType("application", "x-blender", compressible = false, binary = true, fileExtensions = List("blend"))
    
    lazy val `x-blender`: MediaType = xBlender

    lazy val xBlorb: MediaType =
      MediaType("application", "x-blorb", compressible = false, binary = true, fileExtensions = List("blb", "blorb"))
    
    lazy val `x-blorb`: MediaType = xBlorb

    lazy val xBzip: MediaType =
      MediaType("application", "x-bzip", compressible = false, binary = true, fileExtensions = List("bz"))
    
    lazy val `x-bzip`: MediaType = xBzip

    lazy val xBzip2: MediaType =
      MediaType("application", "x-bzip2", compressible = false, binary = true, fileExtensions = List("bz2", "boz"))
    
    lazy val `x-bzip2`: MediaType = xBzip2

    lazy val xCbr: MediaType =
      MediaType("application", "x-cbr", compressible = false, binary = true, fileExtensions = List("cbr", "cba", "cbt", "cbz", "cb7"))
    
    lazy val `x-cbr`: MediaType = xCbr

    lazy val xCdlink: MediaType =
      MediaType("application", "x-cdlink", compressible = false, binary = true, fileExtensions = List("vcd"))
    
    lazy val `x-cdlink`: MediaType = xCdlink

    lazy val xCfsCompressed: MediaType =
      MediaType("application", "x-cfs-compressed", compressible = false, binary = true, fileExtensions = List("cfs"))
    
    lazy val `x-cfs-compressed`: MediaType = xCfsCompressed

    lazy val xChat: MediaType =
      MediaType("application", "x-chat", compressible = false, binary = true, fileExtensions = List("chat"))
    
    lazy val `x-chat`: MediaType = xChat

    lazy val xChessPgn: MediaType =
      MediaType("application", "x-chess-pgn", compressible = false, binary = true, fileExtensions = List("pgn"))
    
    lazy val `x-chess-pgn`: MediaType = xChessPgn

    lazy val xChromeExtension: MediaType =
      MediaType("application", "x-chrome-extension", compressible = false, binary = true, fileExtensions = List("crx"))
    
    lazy val `x-chrome-extension`: MediaType = xChromeExtension

    lazy val xCocoa: MediaType =
      MediaType("application", "x-cocoa", compressible = false, binary = true, fileExtensions = List("cco"))
    
    lazy val `x-cocoa`: MediaType = xCocoa

    lazy val xCompress: MediaType =
      MediaType("application", "x-compress", compressible = false, binary = true)
    
    lazy val `x-compress`: MediaType = xCompress

    lazy val xCompressed: MediaType =
      MediaType("application", "x-compressed", compressible = false, binary = true, fileExtensions = List("rar"))
    
    lazy val `x-compressed`: MediaType = xCompressed

    lazy val xConference: MediaType =
      MediaType("application", "x-conference", compressible = false, binary = true, fileExtensions = List("nsc"))
    
    lazy val `x-conference`: MediaType = xConference

    lazy val xCpio: MediaType =
      MediaType("application", "x-cpio", compressible = false, binary = true, fileExtensions = List("cpio"))
    
    lazy val `x-cpio`: MediaType = xCpio

    lazy val xCsh: MediaType =
      MediaType("application", "x-csh", compressible = false, binary = true, fileExtensions = List("csh"))
    
    lazy val `x-csh`: MediaType = xCsh

    lazy val xDeb: MediaType =
      MediaType("application", "x-deb", compressible = false, binary = true)
    
    lazy val `x-deb`: MediaType = xDeb

    lazy val xDebianPackage: MediaType =
      MediaType("application", "x-debian-package", compressible = false, binary = true, fileExtensions = List("deb", "udeb"))
    
    lazy val `x-debian-package`: MediaType = xDebianPackage

    lazy val xDgcCompressed: MediaType =
      MediaType("application", "x-dgc-compressed", compressible = false, binary = true, fileExtensions = List("dgc"))
    
    lazy val `x-dgc-compressed`: MediaType = xDgcCompressed

    lazy val xDirector: MediaType =
      MediaType("application", "x-director", compressible = false, binary = true, fileExtensions = List("dir", "dcr", "dxr", "cst", "cct", "cxt", "w3d", "fgd", "swa"))
    
    lazy val `x-director`: MediaType = xDirector

    lazy val xDoom: MediaType =
      MediaType("application", "x-doom", compressible = false, binary = true, fileExtensions = List("wad"))
    
    lazy val `x-doom`: MediaType = xDoom

    lazy val xDtbncxXml: MediaType =
      MediaType("application", "x-dtbncx+xml", compressible = true, binary = true, fileExtensions = List("ncx"))
    
    lazy val `x-dtbncx+xml`: MediaType = xDtbncxXml

    lazy val xDtbookXml: MediaType =
      MediaType("application", "x-dtbook+xml", compressible = true, binary = true, fileExtensions = List("dtb"))
    
    lazy val `x-dtbook+xml`: MediaType = xDtbookXml

    lazy val xDtbresourceXml: MediaType =
      MediaType("application", "x-dtbresource+xml", compressible = true, binary = true, fileExtensions = List("res"))
    
    lazy val `x-dtbresource+xml`: MediaType = xDtbresourceXml

    lazy val xDvi: MediaType =
      MediaType("application", "x-dvi", compressible = false, binary = true, fileExtensions = List("dvi"))
    
    lazy val `x-dvi`: MediaType = xDvi

    lazy val xEnvoy: MediaType =
      MediaType("application", "x-envoy", compressible = false, binary = true, fileExtensions = List("evy"))
    
    lazy val `x-envoy`: MediaType = xEnvoy

    lazy val xEva: MediaType =
      MediaType("application", "x-eva", compressible = false, binary = true, fileExtensions = List("eva"))
    
    lazy val `x-eva`: MediaType = xEva

    lazy val xFontBdf: MediaType =
      MediaType("application", "x-font-bdf", compressible = false, binary = true, fileExtensions = List("bdf"))
    
    lazy val `x-font-bdf`: MediaType = xFontBdf

    lazy val xFontDos: MediaType =
      MediaType("application", "x-font-dos", compressible = false, binary = true)
    
    lazy val `x-font-dos`: MediaType = xFontDos

    lazy val xFontFramemaker: MediaType =
      MediaType("application", "x-font-framemaker", compressible = false, binary = true)
    
    lazy val `x-font-framemaker`: MediaType = xFontFramemaker

    lazy val xFontGhostscript: MediaType =
      MediaType("application", "x-font-ghostscript", compressible = false, binary = true, fileExtensions = List("gsf"))
    
    lazy val `x-font-ghostscript`: MediaType = xFontGhostscript

    lazy val xFontLibgrx: MediaType =
      MediaType("application", "x-font-libgrx", compressible = false, binary = true)
    
    lazy val `x-font-libgrx`: MediaType = xFontLibgrx

    lazy val xFontLinuxPsf: MediaType =
      MediaType("application", "x-font-linux-psf", compressible = false, binary = true, fileExtensions = List("psf"))
    
    lazy val `x-font-linux-psf`: MediaType = xFontLinuxPsf

    lazy val xFontPcf: MediaType =
      MediaType("application", "x-font-pcf", compressible = false, binary = true, fileExtensions = List("pcf"))
    
    lazy val `x-font-pcf`: MediaType = xFontPcf

    lazy val xFontSnf: MediaType =
      MediaType("application", "x-font-snf", compressible = false, binary = true, fileExtensions = List("snf"))
    
    lazy val `x-font-snf`: MediaType = xFontSnf

    lazy val xFontSpeedo: MediaType =
      MediaType("application", "x-font-speedo", compressible = false, binary = true)
    
    lazy val `x-font-speedo`: MediaType = xFontSpeedo

    lazy val xFontSunosNews: MediaType =
      MediaType("application", "x-font-sunos-news", compressible = false, binary = true)
    
    lazy val `x-font-sunos-news`: MediaType = xFontSunosNews

    lazy val xFontType1: MediaType =
      MediaType("application", "x-font-type1", compressible = false, binary = true, fileExtensions = List("pfa", "pfb", "pfm", "afm"))
    
    lazy val `x-font-type1`: MediaType = xFontType1

    lazy val xFontVfont: MediaType =
      MediaType("application", "x-font-vfont", compressible = false, binary = true)
    
    lazy val `x-font-vfont`: MediaType = xFontVfont

    lazy val xFreearc: MediaType =
      MediaType("application", "x-freearc", compressible = false, binary = true, fileExtensions = List("arc"))
    
    lazy val `x-freearc`: MediaType = xFreearc

    lazy val xFuturesplash: MediaType =
      MediaType("application", "x-futuresplash", compressible = false, binary = true, fileExtensions = List("spl"))
    
    lazy val `x-futuresplash`: MediaType = xFuturesplash

    lazy val xGcaCompressed: MediaType =
      MediaType("application", "x-gca-compressed", compressible = false, binary = true, fileExtensions = List("gca"))
    
    lazy val `x-gca-compressed`: MediaType = xGcaCompressed

    lazy val xGlulx: MediaType =
      MediaType("application", "x-glulx", compressible = false, binary = true, fileExtensions = List("ulx"))
    
    lazy val `x-glulx`: MediaType = xGlulx

    lazy val xGnumeric: MediaType =
      MediaType("application", "x-gnumeric", compressible = false, binary = true, fileExtensions = List("gnumeric"))
    
    lazy val `x-gnumeric`: MediaType = xGnumeric

    lazy val xGrampsXml: MediaType =
      MediaType("application", "x-gramps-xml", compressible = false, binary = true, fileExtensions = List("gramps"))
    
    lazy val `x-gramps-xml`: MediaType = xGrampsXml

    lazy val xGtar: MediaType =
      MediaType("application", "x-gtar", compressible = false, binary = true, fileExtensions = List("gtar"))
    
    lazy val `x-gtar`: MediaType = xGtar

    lazy val xGzip: MediaType =
      MediaType("application", "x-gzip", compressible = false, binary = true)
    
    lazy val `x-gzip`: MediaType = xGzip

    lazy val xHdf: MediaType =
      MediaType("application", "x-hdf", compressible = false, binary = true, fileExtensions = List("hdf"))
    
    lazy val `x-hdf`: MediaType = xHdf

    lazy val xHttpdPhp: MediaType =
      MediaType("application", "x-httpd-php", compressible = true, binary = true, fileExtensions = List("php"))
    
    lazy val `x-httpd-php`: MediaType = xHttpdPhp

    lazy val xInstallInstructions: MediaType =
      MediaType("application", "x-install-instructions", compressible = false, binary = true, fileExtensions = List("install"))
    
    lazy val `x-install-instructions`: MediaType = xInstallInstructions

    lazy val xIpynbJson: MediaType =
      MediaType("application", "x-ipynb+json", compressible = true, binary = false, fileExtensions = List("ipynb"))
    
    lazy val `x-ipynb+json`: MediaType = xIpynbJson

    lazy val xIso9660Image: MediaType =
      MediaType("application", "x-iso9660-image", compressible = false, binary = true, fileExtensions = List("iso"))
    
    lazy val `x-iso9660-image`: MediaType = xIso9660Image

    lazy val xIworkKeynoteSffkey: MediaType =
      MediaType("application", "x-iwork-keynote-sffkey", compressible = false, binary = true, fileExtensions = List("key"))
    
    lazy val `x-iwork-keynote-sffkey`: MediaType = xIworkKeynoteSffkey

    lazy val xIworkNumbersSffnumbers: MediaType =
      MediaType("application", "x-iwork-numbers-sffnumbers", compressible = false, binary = true, fileExtensions = List("numbers"))
    
    lazy val `x-iwork-numbers-sffnumbers`: MediaType = xIworkNumbersSffnumbers

    lazy val xIworkPagesSffpages: MediaType =
      MediaType("application", "x-iwork-pages-sffpages", compressible = false, binary = true, fileExtensions = List("pages"))
    
    lazy val `x-iwork-pages-sffpages`: MediaType = xIworkPagesSffpages

    lazy val xJavaArchiveDiff: MediaType =
      MediaType("application", "x-java-archive-diff", compressible = false, binary = true, fileExtensions = List("jardiff"))
    
    lazy val `x-java-archive-diff`: MediaType = xJavaArchiveDiff

    lazy val xJavaJnlpFile: MediaType =
      MediaType("application", "x-java-jnlp-file", compressible = false, binary = true, fileExtensions = List("jnlp"))
    
    lazy val `x-java-jnlp-file`: MediaType = xJavaJnlpFile

    lazy val xJavascript: MediaType =
      MediaType("application", "x-javascript", compressible = true, binary = false)
    
    lazy val `x-javascript`: MediaType = xJavascript

    lazy val xKeepass2: MediaType =
      MediaType("application", "x-keepass2", compressible = false, binary = true, fileExtensions = List("kdbx"))
    
    lazy val `x-keepass2`: MediaType = xKeepass2

    lazy val xLatex: MediaType =
      MediaType("application", "x-latex", compressible = false, binary = true, fileExtensions = List("latex"))
    
    lazy val `x-latex`: MediaType = xLatex

    lazy val xLuaBytecode: MediaType =
      MediaType("application", "x-lua-bytecode", compressible = false, binary = true, fileExtensions = List("luac"))
    
    lazy val `x-lua-bytecode`: MediaType = xLuaBytecode

    lazy val xLzhCompressed: MediaType =
      MediaType("application", "x-lzh-compressed", compressible = false, binary = true, fileExtensions = List("lzh", "lha"))
    
    lazy val `x-lzh-compressed`: MediaType = xLzhCompressed

    lazy val xMakeself: MediaType =
      MediaType("application", "x-makeself", compressible = false, binary = true, fileExtensions = List("run"))
    
    lazy val `x-makeself`: MediaType = xMakeself

    lazy val xMie: MediaType =
      MediaType("application", "x-mie", compressible = false, binary = true, fileExtensions = List("mie"))
    
    lazy val `x-mie`: MediaType = xMie

    lazy val xMobipocketEbook: MediaType =
      MediaType("application", "x-mobipocket-ebook", compressible = false, binary = true, fileExtensions = List("prc", "mobi"))
    
    lazy val `x-mobipocket-ebook`: MediaType = xMobipocketEbook

    lazy val xMpegurl: MediaType =
      MediaType("application", "x-mpegurl", compressible = false, binary = true)
    
    lazy val `x-mpegurl`: MediaType = xMpegurl

    lazy val xMsApplication: MediaType =
      MediaType("application", "x-ms-application", compressible = false, binary = true, fileExtensions = List("application"))
    
    lazy val `x-ms-application`: MediaType = xMsApplication

    lazy val xMsShortcut: MediaType =
      MediaType("application", "x-ms-shortcut", compressible = false, binary = true, fileExtensions = List("lnk"))
    
    lazy val `x-ms-shortcut`: MediaType = xMsShortcut

    lazy val xMsWmd: MediaType =
      MediaType("application", "x-ms-wmd", compressible = false, binary = true, fileExtensions = List("wmd"))
    
    lazy val `x-ms-wmd`: MediaType = xMsWmd

    lazy val xMsWmz: MediaType =
      MediaType("application", "x-ms-wmz", compressible = false, binary = true, fileExtensions = List("wmz"))
    
    lazy val `x-ms-wmz`: MediaType = xMsWmz

    lazy val xMsXbap: MediaType =
      MediaType("application", "x-ms-xbap", compressible = false, binary = true, fileExtensions = List("xbap"))
    
    lazy val `x-ms-xbap`: MediaType = xMsXbap

    lazy val xMsaccess: MediaType =
      MediaType("application", "x-msaccess", compressible = false, binary = true, fileExtensions = List("mdb"))
    
    lazy val `x-msaccess`: MediaType = xMsaccess

    lazy val xMsbinder: MediaType =
      MediaType("application", "x-msbinder", compressible = false, binary = true, fileExtensions = List("obd"))
    
    lazy val `x-msbinder`: MediaType = xMsbinder

    lazy val xMscardfile: MediaType =
      MediaType("application", "x-mscardfile", compressible = false, binary = true, fileExtensions = List("crd"))
    
    lazy val `x-mscardfile`: MediaType = xMscardfile

    lazy val xMsclip: MediaType =
      MediaType("application", "x-msclip", compressible = false, binary = true, fileExtensions = List("clp"))
    
    lazy val `x-msclip`: MediaType = xMsclip

    lazy val xMsdosProgram: MediaType =
      MediaType("application", "x-msdos-program", compressible = false, binary = true, fileExtensions = List("exe"))
    
    lazy val `x-msdos-program`: MediaType = xMsdosProgram

    lazy val xMsdownload: MediaType =
      MediaType("application", "x-msdownload", compressible = false, binary = true, fileExtensions = List("exe", "dll", "com", "bat", "msi"))
    
    lazy val `x-msdownload`: MediaType = xMsdownload

    lazy val xMsmediaview: MediaType =
      MediaType("application", "x-msmediaview", compressible = false, binary = true, fileExtensions = List("mvb", "m13", "m14"))
    
    lazy val `x-msmediaview`: MediaType = xMsmediaview

    lazy val xMsmetafile: MediaType =
      MediaType("application", "x-msmetafile", compressible = false, binary = true, fileExtensions = List("wmf", "wmz", "emf", "emz"))
    
    lazy val `x-msmetafile`: MediaType = xMsmetafile

    lazy val xMsmoney: MediaType =
      MediaType("application", "x-msmoney", compressible = false, binary = true, fileExtensions = List("mny"))
    
    lazy val `x-msmoney`: MediaType = xMsmoney

    lazy val xMspublisher: MediaType =
      MediaType("application", "x-mspublisher", compressible = false, binary = true, fileExtensions = List("pub"))
    
    lazy val `x-mspublisher`: MediaType = xMspublisher

    lazy val xMsschedule: MediaType =
      MediaType("application", "x-msschedule", compressible = false, binary = true, fileExtensions = List("scd"))
    
    lazy val `x-msschedule`: MediaType = xMsschedule

    lazy val xMsterminal: MediaType =
      MediaType("application", "x-msterminal", compressible = false, binary = true, fileExtensions = List("trm"))
    
    lazy val `x-msterminal`: MediaType = xMsterminal

    lazy val xMswrite: MediaType =
      MediaType("application", "x-mswrite", compressible = false, binary = true, fileExtensions = List("wri"))
    
    lazy val `x-mswrite`: MediaType = xMswrite

    lazy val xNetcdf: MediaType =
      MediaType("application", "x-netcdf", compressible = false, binary = true, fileExtensions = List("nc", "cdf"))
    
    lazy val `x-netcdf`: MediaType = xNetcdf

    lazy val xNsProxyAutoconfig: MediaType =
      MediaType("application", "x-ns-proxy-autoconfig", compressible = true, binary = true, fileExtensions = List("pac"))
    
    lazy val `x-ns-proxy-autoconfig`: MediaType = xNsProxyAutoconfig

    lazy val xNzb: MediaType =
      MediaType("application", "x-nzb", compressible = false, binary = true, fileExtensions = List("nzb"))
    
    lazy val `x-nzb`: MediaType = xNzb

    lazy val xPerl: MediaType =
      MediaType("application", "x-perl", compressible = false, binary = true, fileExtensions = List("pl", "pm"))
    
    lazy val `x-perl`: MediaType = xPerl

    lazy val xPilot: MediaType =
      MediaType("application", "x-pilot", compressible = false, binary = true, fileExtensions = List("prc", "pdb"))
    
    lazy val `x-pilot`: MediaType = xPilot

    lazy val xPkcs12: MediaType =
      MediaType("application", "x-pkcs12", compressible = false, binary = true, fileExtensions = List("p12", "pfx"))
    
    lazy val `x-pkcs12`: MediaType = xPkcs12

    lazy val xPkcs7Certificates: MediaType =
      MediaType("application", "x-pkcs7-certificates", compressible = false, binary = true, fileExtensions = List("p7b", "spc"))
    
    lazy val `x-pkcs7-certificates`: MediaType = xPkcs7Certificates

    lazy val xPkcs7Certreqresp: MediaType =
      MediaType("application", "x-pkcs7-certreqresp", compressible = false, binary = true, fileExtensions = List("p7r"))
    
    lazy val `x-pkcs7-certreqresp`: MediaType = xPkcs7Certreqresp

    lazy val xPkiMessage: MediaType =
      MediaType("application", "x-pki-message", compressible = false, binary = true)
    
    lazy val `x-pki-message`: MediaType = xPkiMessage

    lazy val xRarCompressed: MediaType =
      MediaType("application", "x-rar-compressed", compressible = false, binary = true, fileExtensions = List("rar"))
    
    lazy val `x-rar-compressed`: MediaType = xRarCompressed

    lazy val xRedhatPackageManager: MediaType =
      MediaType("application", "x-redhat-package-manager", compressible = false, binary = true, fileExtensions = List("rpm"))
    
    lazy val `x-redhat-package-manager`: MediaType = xRedhatPackageManager

    lazy val xResearchInfoSystems: MediaType =
      MediaType("application", "x-research-info-systems", compressible = false, binary = true, fileExtensions = List("ris"))
    
    lazy val `x-research-info-systems`: MediaType = xResearchInfoSystems

    lazy val xSea: MediaType =
      MediaType("application", "x-sea", compressible = false, binary = true, fileExtensions = List("sea"))
    
    lazy val `x-sea`: MediaType = xSea

    lazy val xSh: MediaType =
      MediaType("application", "x-sh", compressible = true, binary = true, fileExtensions = List("sh"))
    
    lazy val `x-sh`: MediaType = xSh

    lazy val xShar: MediaType =
      MediaType("application", "x-shar", compressible = false, binary = true, fileExtensions = List("shar"))
    
    lazy val `x-shar`: MediaType = xShar

    lazy val xShockwaveFlash: MediaType =
      MediaType("application", "x-shockwave-flash", compressible = false, binary = true, fileExtensions = List("swf"))
    
    lazy val `x-shockwave-flash`: MediaType = xShockwaveFlash

    lazy val xSilverlightApp: MediaType =
      MediaType("application", "x-silverlight-app", compressible = false, binary = true, fileExtensions = List("xap"))
    
    lazy val `x-silverlight-app`: MediaType = xSilverlightApp

    lazy val xSql: MediaType =
      MediaType("application", "x-sql", compressible = false, binary = true, fileExtensions = List("sql"))
    
    lazy val `x-sql`: MediaType = xSql

    lazy val xStuffit: MediaType =
      MediaType("application", "x-stuffit", compressible = false, binary = true, fileExtensions = List("sit"))
    
    lazy val `x-stuffit`: MediaType = xStuffit

    lazy val xStuffitx: MediaType =
      MediaType("application", "x-stuffitx", compressible = false, binary = true, fileExtensions = List("sitx"))
    
    lazy val `x-stuffitx`: MediaType = xStuffitx

    lazy val xSubrip: MediaType =
      MediaType("application", "x-subrip", compressible = false, binary = true, fileExtensions = List("srt"))
    
    lazy val `x-subrip`: MediaType = xSubrip

    lazy val xSv4cpio: MediaType =
      MediaType("application", "x-sv4cpio", compressible = false, binary = true, fileExtensions = List("sv4cpio"))
    
    lazy val `x-sv4cpio`: MediaType = xSv4cpio

    lazy val xSv4crc: MediaType =
      MediaType("application", "x-sv4crc", compressible = false, binary = true, fileExtensions = List("sv4crc"))
    
    lazy val `x-sv4crc`: MediaType = xSv4crc

    lazy val xT3vmImage: MediaType =
      MediaType("application", "x-t3vm-image", compressible = false, binary = true, fileExtensions = List("t3"))
    
    lazy val `x-t3vm-image`: MediaType = xT3vmImage

    lazy val xTads: MediaType =
      MediaType("application", "x-tads", compressible = false, binary = true, fileExtensions = List("gam"))
    
    lazy val `x-tads`: MediaType = xTads

    lazy val xTar: MediaType =
      MediaType("application", "x-tar", compressible = true, binary = true, fileExtensions = List("tar"))
    
    lazy val `x-tar`: MediaType = xTar

    lazy val xTcl: MediaType =
      MediaType("application", "x-tcl", compressible = false, binary = true, fileExtensions = List("tcl", "tk"))
    
    lazy val `x-tcl`: MediaType = xTcl

    lazy val xTex: MediaType =
      MediaType("application", "x-tex", compressible = false, binary = true, fileExtensions = List("tex"))
    
    lazy val `x-tex`: MediaType = xTex

    lazy val xTexTfm: MediaType =
      MediaType("application", "x-tex-tfm", compressible = false, binary = true, fileExtensions = List("tfm"))
    
    lazy val `x-tex-tfm`: MediaType = xTexTfm

    lazy val xTexinfo: MediaType =
      MediaType("application", "x-texinfo", compressible = false, binary = true, fileExtensions = List("texinfo", "texi"))
    
    lazy val `x-texinfo`: MediaType = xTexinfo

    lazy val xTgif: MediaType =
      MediaType("application", "x-tgif", compressible = false, binary = true, fileExtensions = List("obj"))
    
    lazy val `x-tgif`: MediaType = xTgif

    lazy val xUstar: MediaType =
      MediaType("application", "x-ustar", compressible = false, binary = true, fileExtensions = List("ustar"))
    
    lazy val `x-ustar`: MediaType = xUstar

    lazy val xVirtualboxHdd: MediaType =
      MediaType("application", "x-virtualbox-hdd", compressible = true, binary = true, fileExtensions = List("hdd"))
    
    lazy val `x-virtualbox-hdd`: MediaType = xVirtualboxHdd

    lazy val xVirtualboxOva: MediaType =
      MediaType("application", "x-virtualbox-ova", compressible = true, binary = true, fileExtensions = List("ova"))
    
    lazy val `x-virtualbox-ova`: MediaType = xVirtualboxOva

    lazy val xVirtualboxOvf: MediaType =
      MediaType("application", "x-virtualbox-ovf", compressible = true, binary = true, fileExtensions = List("ovf"))
    
    lazy val `x-virtualbox-ovf`: MediaType = xVirtualboxOvf

    lazy val xVirtualboxVbox: MediaType =
      MediaType("application", "x-virtualbox-vbox", compressible = true, binary = true, fileExtensions = List("vbox"))
    
    lazy val `x-virtualbox-vbox`: MediaType = xVirtualboxVbox

    lazy val xVirtualboxVboxExtpack: MediaType =
      MediaType("application", "x-virtualbox-vbox-extpack", compressible = false, binary = true, fileExtensions = List("vbox-extpack"))
    
    lazy val `x-virtualbox-vbox-extpack`: MediaType = xVirtualboxVboxExtpack

    lazy val xVirtualboxVdi: MediaType =
      MediaType("application", "x-virtualbox-vdi", compressible = true, binary = true, fileExtensions = List("vdi"))
    
    lazy val `x-virtualbox-vdi`: MediaType = xVirtualboxVdi

    lazy val xVirtualboxVhd: MediaType =
      MediaType("application", "x-virtualbox-vhd", compressible = true, binary = true, fileExtensions = List("vhd"))
    
    lazy val `x-virtualbox-vhd`: MediaType = xVirtualboxVhd

    lazy val xVirtualboxVmdk: MediaType =
      MediaType("application", "x-virtualbox-vmdk", compressible = true, binary = true, fileExtensions = List("vmdk"))
    
    lazy val `x-virtualbox-vmdk`: MediaType = xVirtualboxVmdk

    lazy val xWaisSource: MediaType =
      MediaType("application", "x-wais-source", compressible = false, binary = true, fileExtensions = List("src"))
    
    lazy val `x-wais-source`: MediaType = xWaisSource

    lazy val xWebAppManifestJson: MediaType =
      MediaType("application", "x-web-app-manifest+json", compressible = true, binary = false, fileExtensions = List("webapp"))
    
    lazy val `x-web-app-manifest+json`: MediaType = xWebAppManifestJson

    lazy val xWwwFormUrlencoded: MediaType =
      MediaType("application", "x-www-form-urlencoded", compressible = true, binary = true)
    
    lazy val `x-www-form-urlencoded`: MediaType = xWwwFormUrlencoded

    lazy val xX509CaCert: MediaType =
      MediaType("application", "x-x509-ca-cert", compressible = false, binary = true, fileExtensions = List("der", "crt", "pem"))
    
    lazy val `x-x509-ca-cert`: MediaType = xX509CaCert

    lazy val xX509CaRaCert: MediaType =
      MediaType("application", "x-x509-ca-ra-cert", compressible = false, binary = true)
    
    lazy val `x-x509-ca-ra-cert`: MediaType = xX509CaRaCert

    lazy val xX509NextCaCert: MediaType =
      MediaType("application", "x-x509-next-ca-cert", compressible = false, binary = true)
    
    lazy val `x-x509-next-ca-cert`: MediaType = xX509NextCaCert

    lazy val xXfig: MediaType =
      MediaType("application", "x-xfig", compressible = false, binary = true, fileExtensions = List("fig"))
    
    lazy val `x-xfig`: MediaType = xXfig

    lazy val xXliffXml: MediaType =
      MediaType("application", "x-xliff+xml", compressible = true, binary = true, fileExtensions = List("xlf"))
    
    lazy val `x-xliff+xml`: MediaType = xXliffXml

    lazy val xXpinstall: MediaType =
      MediaType("application", "x-xpinstall", compressible = false, binary = true, fileExtensions = List("xpi"))
    
    lazy val `x-xpinstall`: MediaType = xXpinstall

    lazy val xXz: MediaType =
      MediaType("application", "x-xz", compressible = false, binary = true, fileExtensions = List("xz"))
    
    lazy val `x-xz`: MediaType = xXz

    lazy val xZipCompressed: MediaType =
      MediaType("application", "x-zip-compressed", compressible = false, binary = true, fileExtensions = List("zip"))
    
    lazy val `x-zip-compressed`: MediaType = xZipCompressed

    lazy val xZmachine: MediaType =
      MediaType("application", "x-zmachine", compressible = false, binary = true, fileExtensions = List("z1", "z2", "z3", "z4", "z5", "z6", "z7", "z8"))
    
    lazy val `x-zmachine`: MediaType = xZmachine

    lazy val x400Bp: MediaType =
      MediaType("application", "x400-bp", compressible = false, binary = true)
    
    lazy val `x400-bp`: MediaType = x400Bp

    lazy val xacmlXml: MediaType =
      MediaType("application", "xacml+xml", compressible = true, binary = true)
    
    lazy val `xacml+xml`: MediaType = xacmlXml

    lazy val xamlXml: MediaType =
      MediaType("application", "xaml+xml", compressible = true, binary = true, fileExtensions = List("xaml"))
    
    lazy val `xaml+xml`: MediaType = xamlXml

    lazy val xcapAttXml: MediaType =
      MediaType("application", "xcap-att+xml", compressible = true, binary = true, fileExtensions = List("xav"))
    
    lazy val `xcap-att+xml`: MediaType = xcapAttXml

    lazy val xcapCapsXml: MediaType =
      MediaType("application", "xcap-caps+xml", compressible = true, binary = true, fileExtensions = List("xca"))
    
    lazy val `xcap-caps+xml`: MediaType = xcapCapsXml

    lazy val xcapDiffXml: MediaType =
      MediaType("application", "xcap-diff+xml", compressible = true, binary = true, fileExtensions = List("xdf"))
    
    lazy val `xcap-diff+xml`: MediaType = xcapDiffXml

    lazy val xcapElXml: MediaType =
      MediaType("application", "xcap-el+xml", compressible = true, binary = true, fileExtensions = List("xel"))
    
    lazy val `xcap-el+xml`: MediaType = xcapElXml

    lazy val xcapErrorXml: MediaType =
      MediaType("application", "xcap-error+xml", compressible = true, binary = true)
    
    lazy val `xcap-error+xml`: MediaType = xcapErrorXml

    lazy val xcapNsXml: MediaType =
      MediaType("application", "xcap-ns+xml", compressible = true, binary = true, fileExtensions = List("xns"))
    
    lazy val `xcap-ns+xml`: MediaType = xcapNsXml

    lazy val xconConferenceInfoXml: MediaType =
      MediaType("application", "xcon-conference-info+xml", compressible = true, binary = true)
    
    lazy val `xcon-conference-info+xml`: MediaType = xconConferenceInfoXml

    lazy val xconConferenceInfoDiffXml: MediaType =
      MediaType("application", "xcon-conference-info-diff+xml", compressible = true, binary = true)
    
    lazy val `xcon-conference-info-diff+xml`: MediaType = xconConferenceInfoDiffXml

    lazy val xencXml: MediaType =
      MediaType("application", "xenc+xml", compressible = true, binary = true, fileExtensions = List("xenc"))
    
    lazy val `xenc+xml`: MediaType = xencXml

    lazy val xfdf: MediaType =
      MediaType("application", "xfdf", compressible = false, binary = true, fileExtensions = List("xfdf"))

    lazy val xhtmlXml: MediaType =
      MediaType("application", "xhtml+xml", compressible = true, binary = true, fileExtensions = List("xhtml", "xht"))
    
    lazy val `xhtml+xml`: MediaType = xhtmlXml

    lazy val xhtmlVoiceXml: MediaType =
      MediaType("application", "xhtml-voice+xml", compressible = true, binary = true)
    
    lazy val `xhtml-voice+xml`: MediaType = xhtmlVoiceXml

    lazy val xliffXml: MediaType =
      MediaType("application", "xliff+xml", compressible = true, binary = true, fileExtensions = List("xlf"))
    
    lazy val `xliff+xml`: MediaType = xliffXml

    lazy val xml: MediaType =
      MediaType("application", "xml", compressible = true, binary = false, fileExtensions = List("xml", "xsl", "xsd", "rng"))

    lazy val xmlDtd: MediaType =
      MediaType("application", "xml-dtd", compressible = true, binary = false, fileExtensions = List("dtd"))
    
    lazy val `xml-dtd`: MediaType = xmlDtd

    lazy val xmlExternalParsedEntity: MediaType =
      MediaType("application", "xml-external-parsed-entity", compressible = false, binary = false)
    
    lazy val `xml-external-parsed-entity`: MediaType = xmlExternalParsedEntity

    lazy val xmlPatchXml: MediaType =
      MediaType("application", "xml-patch+xml", compressible = true, binary = false)
    
    lazy val `xml-patch+xml`: MediaType = xmlPatchXml

    lazy val xmppXml: MediaType =
      MediaType("application", "xmpp+xml", compressible = true, binary = true)
    
    lazy val `xmpp+xml`: MediaType = xmppXml

    lazy val xopXml: MediaType =
      MediaType("application", "xop+xml", compressible = true, binary = true, fileExtensions = List("xop"))
    
    lazy val `xop+xml`: MediaType = xopXml

    lazy val xprocXml: MediaType =
      MediaType("application", "xproc+xml", compressible = true, binary = true, fileExtensions = List("xpl"))
    
    lazy val `xproc+xml`: MediaType = xprocXml

    lazy val xsltXml: MediaType =
      MediaType("application", "xslt+xml", compressible = true, binary = true, fileExtensions = List("xsl", "xslt"))
    
    lazy val `xslt+xml`: MediaType = xsltXml

    lazy val xspfXml: MediaType =
      MediaType("application", "xspf+xml", compressible = true, binary = true, fileExtensions = List("xspf"))
    
    lazy val `xspf+xml`: MediaType = xspfXml

    lazy val xvXml: MediaType =
      MediaType("application", "xv+xml", compressible = true, binary = true, fileExtensions = List("mxml", "xhvml", "xvml", "xvm"))
    
    lazy val `xv+xml`: MediaType = xvXml

    lazy val yaml: MediaType =
      MediaType("application", "yaml", compressible = true, binary = true)

    lazy val yang: MediaType =
      MediaType("application", "yang", compressible = false, binary = true, fileExtensions = List("yang"))

    lazy val yangDataCbor: MediaType =
      MediaType("application", "yang-data+cbor", compressible = false, binary = true)
    
    lazy val `yang-data+cbor`: MediaType = yangDataCbor

    lazy val yangDataJson: MediaType =
      MediaType("application", "yang-data+json", compressible = true, binary = false)
    
    lazy val `yang-data+json`: MediaType = yangDataJson

    lazy val yangDataXml: MediaType =
      MediaType("application", "yang-data+xml", compressible = true, binary = true)
    
    lazy val `yang-data+xml`: MediaType = yangDataXml

    lazy val yangPatchJson: MediaType =
      MediaType("application", "yang-patch+json", compressible = true, binary = false)
    
    lazy val `yang-patch+json`: MediaType = yangPatchJson

    lazy val yangPatchXml: MediaType =
      MediaType("application", "yang-patch+xml", compressible = true, binary = true)
    
    lazy val `yang-patch+xml`: MediaType = yangPatchXml

    lazy val yangSidJson: MediaType =
      MediaType("application", "yang-sid+json", compressible = true, binary = false)
    
    lazy val `yang-sid+json`: MediaType = yangSidJson

    lazy val yinXml: MediaType =
      MediaType("application", "yin+xml", compressible = true, binary = true, fileExtensions = List("yin"))
    
    lazy val `yin+xml`: MediaType = yinXml

    lazy val zip: MediaType =
      MediaType("application", "zip", compressible = false, binary = true, fileExtensions = List("zip"))

    lazy val zipDotlottie: MediaType =
      MediaType("application", "zip+dotlottie", compressible = false, binary = true, fileExtensions = List("lottie"))
    
    lazy val `zip+dotlottie`: MediaType = zipDotlottie

    lazy val zlib: MediaType =
      MediaType("application", "zlib", compressible = false, binary = true)

    lazy val zstd: MediaType =
      MediaType("application", "zstd", compressible = false, binary = true)

    lazy val any: MediaType = MediaType("application", "*")

    lazy val all: List[MediaType] = List(
      `1d-interleaved-parityfec`,
      `3gpdash-qoe-report+xml`,
      `3gpp-ims+xml`,
      `3gpp-mbs-object-manifest+json`,
      `3gpp-mbs-user-service-descriptions+json`,
      `3gpp-media-delivery-metrics-report+json`,
      `3gpphal+json`,
      `3gpphalforms+json`,
      a2l,
      aasZip,
      aceCbor,
      aceJson,
      aceGroupcommCbor,
      aceTrlCbor,
      activemessage,
      activityJson,
      aifCbor,
      aifJson,
      altoCdniJson,
      altoCdnifilterJson,
      altoCostmapJson,
      altoCostmapfilterJson,
      altoDirectoryJson,
      altoEndpointcostJson,
      altoEndpointcostparamsJson,
      altoEndpointpropJson,
      altoEndpointpropparamsJson,
      altoErrorJson,
      altoNetworkmapJson,
      altoNetworkmapfilterJson,
      altoPropmapJson,
      altoPropmapparamsJson,
      altoTipsJson,
      altoTipsparamsJson,
      altoUpdatestreamcontrolJson,
      altoUpdatestreamparamsJson,
      aml,
      andrewInset,
      appinstaller,
      applefile,
      applixware,
      appx,
      appxbundle,
      asyncapiJson,
      asyncapiYaml,
      atJwt,
      atf,
      atfx,
      atomXml,
      atomcatXml,
      atomdeletedXml,
      atomicmail,
      atomsvcXml,
      atscDwdXml,
      atscDynamicEventMessage,
      atscHeldXml,
      atscRdtJson,
      atscRsatXml,
      atxml,
      authPolicyXml,
      automationmlAmlXml,
      automationmlAmlxZip,
      bacnetXddZip,
      batchSmtp,
      bdoc,
      beepXml,
      bufr,
      c2pa,
      calendarJson,
      calendarXml,
      callCompletion,
      cals1840,
      captiveJson,
      cbor,
      cborSeq,
      cccex,
      ccmpXml,
      ccxmlXml,
      cdaXml,
      cdfxXml,
      cdmiCapability,
      cdmiContainer,
      cdmiDomain,
      cdmiObject,
      cdmiQueue,
      cdni,
      ceCbor,
      cea,
      cea2018Xml,
      cellmlXml,
      cfw,
      cid,
      cidEdhocCborSeq,
      cityJson,
      cityJsonSeq,
      clr,
      clueXml,
      clueInfoXml,
      cms,
      cmwCbor,
      cmwCose,
      cmwJson,
      cmwJws,
      cnrpXml,
      coapEap,
      coapGroupJson,
      coapPayload,
      commonground,
      conciseProblemDetailsCbor,
      conferenceInfoXml,
      cose,
      coseKey,
      coseKeySet,
      coseX509,
      cplXml,
      csrattrs,
      cstaXml,
      cstadataXml,
      csvmJson,
      cuSeeme,
      cwl,
      cwlJson,
      cwlYaml,
      cwt,
      cybercash,
      dart,
      dashXml,
      dashPatchXml,
      dashdelta,
      davmountXml,
      dcaRft,
      dcd,
      decDx,
      dialogInfoXml,
      dicom,
      dicomJson,
      dicomXml,
      did,
      dii,
      dit,
      dns,
      dnsJson,
      dnsMessage,
      docbookXml,
      dotsCbor,
      dpopJwt,
      dskppXml,
      dsscDer,
      dsscXml,
      dvcs,
      eatCwt,
      eatJwt,
      eatBunCbor,
      eatBunJson,
      eatUcsCbor,
      eatUcsJson,
      ecmascript,
      edhocCborSeq,
      ediConsent,
      ediX12,
      edifact,
      efi,
      elmJson,
      elmXml,
      emergencycalldataCapXml,
      emergencycalldataCommentXml,
      emergencycalldataControlXml,
      emergencycalldataDeviceinfoXml,
      emergencycalldataEcallMsd,
      emergencycalldataLegacyesnJson,
      emergencycalldataProviderinfoXml,
      emergencycalldataServiceinfoXml,
      emergencycalldataSubscriberinfoXml,
      emergencycalldataVedsXml,
      emmaXml,
      emotionmlXml,
      encaprtp,
      entityStatementJwt,
      eppXml,
      epubZip,
      eshop,
      exi,
      expectCtReportJson,
      explicitRegistrationResponseJwt,
      express,
      fastinfoset,
      fastsoap,
      fdf,
      fdtXml,
      fhirJson,
      fhirXml,
      fidoTrustedAppsJson,
      fits,
      flexfec,
      fontSfnt,
      fontTdpfr,
      fontWoff,
      frameworkAttributesXml,
      geoJson,
      geoJsonSeq,
      geofeedCsv,
      geopackageSqlite3,
      geoposeJson,
      geoxacmlJson,
      geoxacmlXml,
      gltfBuffer,
      gmlXml,
      gnapBindingJws,
      gnapBindingJwsd,
      gnapBindingRotationJws,
      gnapBindingRotationJwsd,
      gpxXml,
      grib,
      gxf,
      gzip,
      h224,
      heldXml,
      hjson,
      hl7v2Xml,
      http,
      hyperstudio,
      ibeKeyRequestXml,
      ibePkgReplyXml,
      ibePpData,
      iges,
      imIscomposingXml,
      index,
      indexCmd,
      indexObj,
      indexResponse,
      indexVnd,
      inkmlXml,
      iotp,
      ipfix,
      ipp,
      isup,
      itsXml,
      javaArchive,
      javaSerializedObject,
      javaVm,
      javascript,
      jf2feedJson,
      jose,
      joseJson,
      jrdJson,
      jscalendarJson,
      jscontactJson,
      json,
      jsonPatchJson,
      jsonPatchQueryJson,
      jsonSeq,
      json5,
      jsonmlJson,
      jsonpath,
      jwkJson,
      jwkSetJson,
      jwkSetJwt,
      jwt,
      kbJwt,
      kblXml,
      kpmlRequestXml,
      kpmlResponseXml,
      ldJson,
      lgrXml,
      linkFormat,
      linkset,
      linksetJson,
      loadControlXml,
      logoutJwt,
      lostXml,
      lostsyncXml,
      lpfZip,
      lxf,
      macBinhex40,
      macCompactpro,
      macwriteii,
      madsXml,
      manifestJson,
      marc,
      marcxmlXml,
      mathematica,
      mathmlXml,
      mathmlContentXml,
      mathmlPresentationXml,
      mbmsAssociatedProcedureDescriptionXml,
      mbmsDeregisterXml,
      mbmsEnvelopeXml,
      mbmsMskXml,
      mbmsMskResponseXml,
      mbmsProtectionDescriptionXml,
      mbmsReceptionReportXml,
      mbmsRegisterXml,
      mbmsRegisterResponseXml,
      mbmsScheduleXml,
      mbmsUserServiceDescriptionXml,
      mbox,
      measuredComponentCbor,
      measuredComponentJson,
      mediaPolicyDatasetXml,
      mediaControlXml,
      mediaservercontrolXml,
      mergePatchJson,
      metalinkXml,
      metalink4Xml,
      metsXml,
      mf4,
      mikey,
      mipc,
      missingBlocksCborSeq,
      mmtAeiXml,
      mmtUsdXml,
      modsXml,
      mossKeys,
      mossSignature,
      mosskeyData,
      mosskeyRequest,
      mp21,
      mp4,
      mpeg4Generic,
      mpeg4Iod,
      mpeg4IodXmt,
      mrbConsumerXml,
      mrbPublishXml,
      mscIvrXml,
      mscMixerXml,
      msix,
      msixbundle,
      msword,
      mudJson,
      multipartCore,
      mxf,
      nQuads,
      nTriples,
      nasdata,
      newsCheckgroups,
      newsGroupinfo,
      newsTransmission,
      nlsmlXml,
      node,
      nss,
      oauthAuthzReqJwt,
      obliviousDnsMessage,
      ocspRequest,
      ocspResponse,
      octetStream,
      oda,
      odmXml,
      odx,
      oebpsPackageXml,
      ogg,
      ohttpKeys,
      omdocXml,
      onenote,
      opcNodesetXml,
      oscore,
      oxps,
      p21,
      p21Zip,
      p2pOverlayXml,
      parityfec,
      passport,
      patchOpsErrorXml,
      pdf,
      pdx,
      pemCertificateChain,
      pgpEncrypted,
      pgpKeys,
      pgpSignature,
      picsRules,
      pidfXml,
      pidfDiffXml,
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
      plsXml,
      pocSettingsXml,
      postscript,
      ppspTrackerJson,
      privateTokenIssuerDirectory,
      privateTokenRequest,
      privateTokenResponse,
      problemJson,
      problemXml,
      protobuf,
      protobufJson,
      provenanceXml,
      providedClaimsJwt,
      prsAlvestrandTitraxSheet,
      prsCww,
      prsCyn,
      prsHpubZip,
      prsImpliedDocumentXml,
      prsImpliedExecutable,
      prsImpliedObjectJson,
      prsImpliedObjectJsonSeq,
      prsImpliedObjectYaml,
      prsImpliedStructure,
      prsMayfile,
      prsNprend,
      prsPlucker,
      prsRdfXmlCrypt,
      prsSclt,
      prsVcfbzip2,
      prsXsfXml,
      pskcXml,
      pvdJson,
      qsig,
      ramlYaml,
      raptorfec,
      rdapJson,
      rdfXml,
      reginfoXml,
      relaxNgCompactSyntax,
      remotePrinting,
      reputonJson,
      resolveResponseJwt,
      resourceListsXml,
      resourceListsDiffXml,
      rfcXml,
      riscos,
      rlmiXml,
      rlsServicesXml,
      routeApdXml,
      routeSTsidXml,
      routeUsdXml,
      rpkiChecklist,
      rpkiGhostbusters,
      rpkiManifest,
      rpkiPublication,
      rpkiRoa,
      rpkiSignedTal,
      rpkiUpdown,
      rsMetadataXml,
      rsdXml,
      rssXml,
      rtf,
      rtploopback,
      rtx,
      samlassertionXml,
      samlmetadataXml,
      sarifJson,
      sarifExternalPropertiesJson,
      sbe,
      sbmlXml,
      scaipXml,
      scimJson,
      scittReceiptCose,
      scittStatementCose,
      scvpCvRequest,
      scvpCvResponse,
      scvpVpRequest,
      scvpVpResponse,
      sdJwt,
      sdJwtJson,
      sdfJson,
      sdp,
      seceventJwt,
      senmlCbor,
      senmlJson,
      senmlXml,
      senmlEtchCbor,
      senmlEtchJson,
      senmlExi,
      sensmlCbor,
      sensmlJson,
      sensmlXml,
      sensmlExi,
      sepXml,
      sepExi,
      sessionInfo,
      setPayment,
      setPaymentInitiation,
      setRegistration,
      setRegistrationInitiation,
      sgml,
      sgmlOpenCatalog,
      shfXml,
      sieve,
      simpleFilterXml,
      simpleMessageSummary,
      simplesymbolcontainer,
      sipc,
      slate,
      smil,
      smilXml,
      smpte336m,
      soapFastinfoset,
      soapXml,
      sparqlQuery,
      sparqlResultsXml,
      spdxJson,
      spiritsEventXml,
      sql,
      srgs,
      srgsXml,
      sruXml,
      ssdlXml,
      sslkeylogfile,
      ssmlXml,
      st211041,
      stixJson,
      stratum,
      suitEnvelopeCose,
      suitReportCose,
      swidCbor,
      swidXml,
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
      taxiiJson,
      tdJson,
      teiXml,
      tetra_isi,
      texinfo,
      thraudXml,
      timestampQuery,
      timestampReply,
      timestampedData,
      tlsrptGzip,
      tlsrptJson,
      tmJson,
      tnauthlist,
      tocCbor,
      tokenIntrospectionJwt,
      toml,
      trickleIceSdpfrag,
      trig,
      trustChainJson,
      trustMarkJwt,
      trustMarkDelegationJwt,
      trustMarkStatusResponseJwt,
      ttmlXml,
      tveTrigger,
      tzif,
      tzifLeap,
      ubjson,
      uccsCbor,
      ujcsJson,
      ulpfec,
      urcGrpsheetXml,
      urcRessheetXml,
      urcTargetdescXml,
      urcUisocketdescXml,
      vc,
      vcCose,
      vcJwt,
      vcSdJwt,
      vcardJson,
      vcardXml,
      vecXml,
      vecPackageGzip,
      vecPackageZip,
      vemmi,
      vividenceScriptfile,
      vnd1000mindsDecisionModelXml,
      vnd1ob,
      vnd3gppProseXml,
      vnd3gppProsePc3aXml,
      vnd3gppProsePc3achXml,
      vnd3gppProsePc3chXml,
      vnd3gppProsePc8Xml,
      vnd3gppV2xLocalServiceInformation,
      vnd3gpp5gnas,
      vnd3gpp5gsa2x,
      vnd3gpp5gsa2xLocalServiceInformation,
      vnd3gpp5gsv2x,
      vnd3gpp5gsv2xLocalServiceInformation,
      vnd3gppAccessTransferEventsXml,
      vnd3gppBsfXml,
      vnd3gppCrsXml,
      vnd3gppCurrentLocationDiscoveryXml,
      vnd3gppGmopXml,
      vnd3gppGtpc,
      vnd3gppInterworkingData,
      vnd3gppLpp,
      vnd3gppMcSignallingEar,
      vnd3gppMcdataAffiliationCommandXml,
      vnd3gppMcdataInfoXml,
      vnd3gppMcdataMsgstoreCtrlRequestXml,
      vnd3gppMcdataPayload,
      vnd3gppMcdataRegroupXml,
      vnd3gppMcdataServiceConfigXml,
      vnd3gppMcdataSignalling,
      vnd3gppMcdataUeConfigXml,
      vnd3gppMcdataUserProfileXml,
      vnd3gppMcpttAffiliationCommandXml,
      vnd3gppMcpttFloorRequestXml,
      vnd3gppMcpttInfoXml,
      vnd3gppMcpttLocationInfoXml,
      vnd3gppMcpttMbmsUsageInfoXml,
      vnd3gppMcpttRegroupXml,
      vnd3gppMcpttServiceConfigXml,
      vnd3gppMcpttSignedXml,
      vnd3gppMcpttUeConfigXml,
      vnd3gppMcpttUeInitConfigXml,
      vnd3gppMcpttUserProfileXml,
      vnd3gppMcvideoAffiliationCommandXml,
      vnd3gppMcvideoInfoXml,
      vnd3gppMcvideoLocationInfoXml,
      vnd3gppMcvideoMbmsUsageInfoXml,
      vnd3gppMcvideoRegroupXml,
      vnd3gppMcvideoServiceConfigXml,
      vnd3gppMcvideoTransmissionRequestXml,
      vnd3gppMcvideoUeConfigXml,
      vnd3gppMcvideoUserProfileXml,
      vnd3gppMidCallXml,
      vnd3gppNgap,
      vnd3gppPfcp,
      vnd3gppPicBwLarge,
      vnd3gppPicBwSmall,
      vnd3gppPicBwVar,
      vnd3gppPinappInfoXml,
      vnd3gppS1ap,
      vnd3gppSealAppCommRequirementsInfoXml,
      vnd3gppSealDataDeliveryInfoCbor,
      vnd3gppSealDataDeliveryInfoXml,
      vnd3gppSealGroupDocXml,
      vnd3gppSealInfoXml,
      vnd3gppSealLocationInfoCbor,
      vnd3gppSealLocationInfoXml,
      vnd3gppSealMbmsUsageInfoXml,
      vnd3gppSealMbsUsageInfoXml,
      vnd3gppSealNetworkQosManagementInfoXml,
      vnd3gppSealNetworkResourceInfoCbor,
      vnd3gppSealUeConfigInfoXml,
      vnd3gppSealUnicastInfoXml,
      vnd3gppSealUserProfileInfoXml,
      vnd3gppSms,
      vnd3gppSmsXml,
      vnd3gppSrvccExtXml,
      vnd3gppSrvccInfoXml,
      vnd3gppStateAndEventInfoXml,
      vnd3gppUssdXml,
      vnd3gppV2x,
      vnd3gppVaeInfoXml,
      vnd3gpp2BcmcsinfoXml,
      vnd3gpp2Sms,
      vnd3gpp2Tcap,
      vnd3lightssoftwareImagescal,
      vnd3mPostItNotes,
      vndAccpacSimplyAso,
      vndAccpacSimplyImp,
      vndAcmAddressxferJson,
      vndAcmChatbotJson,
      vndAcucobol,
      vndAcucorp,
      vndAdobeAirApplicationInstallerPackageZip,
      vndAdobeFlashMovie,
      vndAdobeFormscentralFcdt,
      vndAdobeFxp,
      vndAdobePartialUpload,
      vndAdobeXdpXml,
      vndAdobeXfdf,
      vndAetherImp,
      vndAfpcAfplinedata,
      vndAfpcAfplinedataPagedef,
      vndAfpcCmocaCmresource,
      vndAfpcFocaCharset,
      vndAfpcFocaCodedfont,
      vndAfpcFocaCodepage,
      vndAfpcModca,
      vndAfpcModcaCmtable,
      vndAfpcModcaFormdef,
      vndAfpcModcaMediummap,
      vndAfpcModcaObjectcontainer,
      vndAfpcModcaOverlay,
      vndAfpcModcaPagesegment,
      vndAge,
      vndAhBarcode,
      vndAheadSpace,
      vndAia,
      vndAirzipFilesecureAzf,
      vndAirzipFilesecureAzs,
      vndAmadeusJson,
      vndAmazonEbook,
      vndAmazonMobi8Ebook,
      vndAmericandynamicsAcc,
      vndAmigaAmi,
      vndAmundsenMazeXml,
      vndAndroidOta,
      vndAndroidPackageArchive,
      vndAnki,
      vndAnserWebCertificateIssueInitiation,
      vndAnserWebFundsTransferInitiation,
      vndAntixGameComponent,
      vndApacheArrowFile,
      vndApacheArrowStream,
      vndApacheParquet,
      vndApacheThriftBinary,
      vndApacheThriftCompact,
      vndApacheThriftJson,
      vndApexlang,
      vndApiJson,
      vndAplextorWarrpJson,
      vndApothekendeReservationJson,
      vndAppleInstallerXml,
      vndAppleKeynote,
      vndAppleMpegurl,
      vndAppleNumbers,
      vndApplePages,
      vndApplePkpass,
      vndArastraSwi,
      vndAristanetworksSwi,
      vndArtisanJson,
      vndArtsquare,
      vndAs207960VasConfigJer,
      vndAs207960VasConfigUper,
      vndAs207960VasTapJer,
      vndAs207960VasTapUper,
      vndAstraeaSoftwareIota,
      vndAudiograph,
      vndAutodeskFbx,
      vndAutopackage,
      vndAvalonJson,
      vndAvistarXml,
      vndBalsamiqBmmlXml,
      vndBalsamiqBmpr,
      vndBananaAccounting,
      vndBbfUspError,
      vndBbfUspMsg,
      vndBbfUspMsgJson,
      vndBekitzurStechJson,
      vndBelightsoftLhzdZip,
      vndBelightsoftLhzlZip,
      vndBintMedContent,
      vndBiopaxRdfXml,
      vndBlinkIdbValueWrapper,
      vndBlueiceMultipass,
      vndBluetoothEpOob,
      vndBluetoothLeOob,
      vndBmi,
      vndBpf,
      vndBpf3,
      vndBusinessobjects,
      vndByuUapiJson,
      vndBzip3,
      vndC3vocScheduleXml,
      vndCabJscript,
      vndCanonCpdl,
      vndCanonLips,
      vndCapasystemsPgJson,
      vndCel,
      vndCendioThinlincClientconf,
      vndCenturySystemsTcpStream,
      vndChemdrawXml,
      vndChessPgn,
      vndChipnutsKaraokeMmd,
      vndCiedi,
      vndCinderella,
      vndCirpackIsdnExt,
      vndCitationstylesStyleXml,
      vndClaymore,
      vndCloantoRp9,
      vndClonkC4group,
      vndCluetrustCartomobileConfig,
      vndCluetrustCartomobileConfigPkg,
      vndCncfHelmChartContentV1TarGzip,
      vndCncfHelmChartProvenanceV1Prov,
      vndCncfHelmConfigV1Json,
      vndCoffeescript,
      vndCollabioXodocumentsDocument,
      vndCollabioXodocumentsDocumentTemplate,
      vndCollabioXodocumentsPresentation,
      vndCollabioXodocumentsPresentationTemplate,
      vndCollabioXodocumentsSpreadsheet,
      vndCollabioXodocumentsSpreadsheetTemplate,
      vndCollectionJson,
      vndCollectionDocJson,
      vndCollectionNextJson,
      vndComicbookZip,
      vndComicbookRar,
      vndCommerceBattelle,
      vndCommonspace,
      vndContactCmsg,
      vndCoreosIgnitionJson,
      vndCosmocaller,
      vndCrickClicker,
      vndCrickClickerKeyboard,
      vndCrickClickerPalette,
      vndCrickClickerTemplate,
      vndCrickClickerWordbank,
      vndCriticaltoolsWbsXml,
      vndCryptiiPipeJson,
      vndCryptoShadeFile,
      vndCryptomatorEncrypted,
      vndCryptomatorVault,
      vndCtcPosml,
      vndCtctWsXml,
      vndCupsPdf,
      vndCupsPostscript,
      vndCupsPpd,
      vndCupsRaster,
      vndCupsRaw,
      vndCurl,
      vndCurlCar,
      vndCurlPcurl,
      vndCyanDeanRootXml,
      vndCybank,
      vndCyclonedxJson,
      vndCyclonedxXml,
      vndD2lCoursepackage1p0Zip,
      vndD3mDataset,
      vndD3mProblem,
      vndDart,
      vndDataVisionRdz,
      vndDatalog,
      vndDatapackageJson,
      vndDataresourceJson,
      vndDbf,
      vndDcmpXml,
      vndDebianBinaryPackage,
      vndDeceData,
      vndDeceTtmlXml,
      vndDeceUnspecified,
      vndDeceZip,
      vndDenovoFcselayoutLink,
      vndDesmumeMovie,
      vndDirBiPlateDlNosuffix,
      vndDmDelegationXml,
      vndDna,
      vndDocumentJson,
      vndDolbyMlp,
      vndDolbyMobile1,
      vndDolbyMobile2,
      vndDoremirScorecloudBinaryDocument,
      vndDpgraph,
      vndDreamfactory,
      vndDriveJson,
      vndDsKeypoint,
      vndDtgLocal,
      vndDtgLocalFlash,
      vndDtgLocalHtml,
      vndDvbAit,
      vndDvbDvbislXml,
      vndDvbDvbj,
      vndDvbEsgcontainer,
      vndDvbIpdcdftnotifaccess,
      vndDvbIpdcesgaccess,
      vndDvbIpdcesgaccess2,
      vndDvbIpdcesgpdd,
      vndDvbIpdcroaming,
      vndDvbIptvAlfecBase,
      vndDvbIptvAlfecEnhancement,
      vndDvbNotifAggregateRootXml,
      vndDvbNotifContainerXml,
      vndDvbNotifGenericXml,
      vndDvbNotifIaMsglistXml,
      vndDvbNotifIaRegistrationRequestXml,
      vndDvbNotifIaRegistrationResponseXml,
      vndDvbNotifInitXml,
      vndDvbPfr,
      vndDvbService,
      vndDxr,
      vndDynageo,
      vndDzr,
      vndEasykaraokeCdgdownload,
      vndEcdisUpdate,
      vndEcipRlp,
      vndEclipseDittoJson,
      vndEcowinChart,
      vndEcowinFilerequest,
      vndEcowinFileupdate,
      vndEcowinSeries,
      vndEcowinSeriesrequest,
      vndEcowinSeriesupdate,
      vndEfiImg,
      vndEfiIso,
      vndElnZip,
      vndEmclientAccessrequestXml,
      vndEnliven,
      vndEnphaseEnvoy,
      vndEprintsDataXml,
      vndEpsonEsf,
      vndEpsonMsf,
      vndEpsonQuickanime,
      vndEpsonSalt,
      vndEpsonSsf,
      vndEricssonQuickcall,
      vndErofs,
      vndEspassEspassZip,
      vndEszigno3Xml,
      vndEtsiAocXml,
      vndEtsiAsicEZip,
      vndEtsiAsicSZip,
      vndEtsiCugXml,
      vndEtsiIptvcommandXml,
      vndEtsiIptvdiscoveryXml,
      vndEtsiIptvprofileXml,
      vndEtsiIptvsadBcXml,
      vndEtsiIptvsadCodXml,
      vndEtsiIptvsadNpvrXml,
      vndEtsiIptvserviceXml,
      vndEtsiIptvsyncXml,
      vndEtsiIptvueprofileXml,
      vndEtsiMcidXml,
      vndEtsiMheg5,
      vndEtsiOverloadControlPolicyDatasetXml,
      vndEtsiPstnXml,
      vndEtsiSciXml,
      vndEtsiSimservsXml,
      vndEtsiTimestampToken,
      vndEtsiTslXml,
      vndEtsiTslDer,
      vndEuKasparianCarJson,
      vndEudoraData,
      vndEvolvEcigProfile,
      vndEvolvEcigSettings,
      vndEvolvEcigTheme,
      vndExstreamEmpowerZip,
      vndExstreamPackage,
      vndEzpixAlbum,
      vndEzpixPackage,
      vndFSecureMobile,
      vndFafYaml,
      vndFamilysearchGedcomZip,
      vndFastcopyDiskImage,
      vndFdf,
      vndFdsnMseed,
      vndFdsnSeed,
      vndFdsnStationxmlXml,
      vndFfsns,
      vndFgb,
      vndFiclabFlbZip,
      vndFilmitZfc,
      vndFints,
      vndFiremonkeysCloudcell,
      vndFlographit,
      vndFluxtimeClip,
      vndFontFontforgeSfd,
      vndFramemaker,
      vndFreelogComic,
      vndFrogansFnc,
      vndFrogansLtf,
      vndFscWeblaunch,
      vndFujifilmFbDocuworks,
      vndFujifilmFbDocuworksBinder,
      vndFujifilmFbDocuworksContainer,
      vndFujifilmFbJfiXml,
      vndFujitsuOasys,
      vndFujitsuOasys2,
      vndFujitsuOasys3,
      vndFujitsuOasysgp,
      vndFujitsuOasysprs,
      vndFujixeroxArtEx,
      vndFujixeroxArt4,
      vndFujixeroxDdd,
      vndFujixeroxDocuworks,
      vndFujixeroxDocuworksBinder,
      vndFujixeroxDocuworksContainer,
      vndFujixeroxHbpl,
      vndFutMisnet,
      vndFutoinCbor,
      vndFutoinJson,
      vndFuzzysheet,
      vndG3pixG3fc,
      vndGa4ghPassportJwt,
      vndGenomatixTuxedo,
      vndGenozip,
      vndGenticsGrdJson,
      vndGentooCatmetadataXml,
      vndGentooEbuild,
      vndGentooEclass,
      vndGentooGpkg,
      vndGentooManifest,
      vndGentooPkgmetadataXml,
      vndGentooXpak,
      vndGeoJson,
      vndGeocubeXml,
      vndGeogebraFile,
      vndGeogebraPinboard,
      vndGeogebraSlides,
      vndGeogebraTool,
      vndGeometryExplorer,
      vndGeonext,
      vndGeoplan,
      vndGeospace,
      vndGerber,
      vndGlobalplatformCardContentMgt,
      vndGlobalplatformCardContentMgtResponse,
      vndGmx,
      vndGnuTalerExchangeJson,
      vndGnuTalerMerchantJson,
      vndGoogleAppsAudio,
      vndGoogleAppsDocument,
      vndGoogleAppsDrawing,
      vndGoogleAppsDriveSdk,
      vndGoogleAppsFile,
      vndGoogleAppsFolder,
      vndGoogleAppsForm,
      vndGoogleAppsFusiontable,
      vndGoogleAppsJam,
      vndGoogleAppsMailLayout,
      vndGoogleAppsMap,
      vndGoogleAppsPhoto,
      vndGoogleAppsPresentation,
      vndGoogleAppsScript,
      vndGoogleAppsShortcut,
      vndGoogleAppsSite,
      vndGoogleAppsSpreadsheet,
      vndGoogleAppsUnknown,
      vndGoogleAppsVideo,
      vndGoogleEarthKmlXml,
      vndGoogleEarthKmz,
      vndGovSkEFormXml,
      vndGovSkEFormZip,
      vndGovSkXmldatacontainerXml,
      vndGpxseeMapXml,
      vndGrafeq,
      vndGridmp,
      vndGrooveAccount,
      vndGrooveHelp,
      vndGrooveIdentityMessage,
      vndGrooveInjector,
      vndGrooveToolMessage,
      vndGrooveToolTemplate,
      vndGrooveVcard,
      vndHalJson,
      vndHalXml,
      vndHandheldEntertainmentXml,
      vndHbci,
      vndHcJson,
      vndHclBireports,
      vndHdt,
      vndHerokuJson,
      vndHheLessonPlayer,
      vndHpHpgl,
      vndHpHpid,
      vndHpHps,
      vndHpJlyt,
      vndHpPcl,
      vndHpPclxl,
      vndHsl,
      vndHttphone,
      vndHydrostatixSofData,
      vndHyperJson,
      vndHyperItemJson,
      vndHyperdriveJson,
      vndHzn3dCrossword,
      vndIbmAfplinedata,
      vndIbmElectronicMedia,
      vndIbmMinipay,
      vndIbmModcap,
      vndIbmRightsManagement,
      vndIbmSecureContainer,
      vndIccprofile,
      vndIeee1905,
      vndIgloader,
      vndImagemeterFolderZip,
      vndImagemeterImageZip,
      vndImmervisionIvp,
      vndImmervisionIvu,
      vndImsImsccv1p1,
      vndImsImsccv1p2,
      vndImsImsccv1p3,
      vndImsLisV2ResultJson,
      vndImsLtiV2ToolconsumerprofileJson,
      vndImsLtiV2ToolproxyJson,
      vndImsLtiV2ToolproxyIdJson,
      vndImsLtiV2ToolsettingsJson,
      vndImsLtiV2ToolsettingsSimpleJson,
      vndInformedcontrolRmsXml,
      vndInformixVisionary,
      vndInfotechProject,
      vndInfotechProjectXml,
      vndInnopathWampNotification,
      vndInsorsIgm,
      vndInterconFormnet,
      vndIntergeo,
      vndIntertrustDigibox,
      vndIntertrustNncp,
      vndIntuQbo,
      vndIntuQfx,
      vndIpfsIpnsRecord,
      vndIpldCar,
      vndIpldDagCbor,
      vndIpldDagJson,
      vndIpldRaw,
      vndIptcG2CatalogitemXml,
      vndIptcG2ConceptitemXml,
      vndIptcG2KnowledgeitemXml,
      vndIptcG2NewsitemXml,
      vndIptcG2NewsmessageXml,
      vndIptcG2PackageitemXml,
      vndIptcG2PlanningitemXml,
      vndIpunpluggedRcprofile,
      vndIrepositoryPackageXml,
      vndIsXpr,
      vndIsacFcs,
      vndIso1178310Zip,
      vndJam,
      vndJapannetDirectoryService,
      vndJapannetJpnstoreWakeup,
      vndJapannetPaymentWakeup,
      vndJapannetRegistration,
      vndJapannetRegistrationWakeup,
      vndJapannetSetstoreWakeup,
      vndJapannetVerification,
      vndJapannetVerificationWakeup,
      vndJcpJavameMidletRms,
      vndJisp,
      vndJoostJodaArchive,
      vndJskIsdnNgn,
      vndKahootz,
      vndKdeKarbon,
      vndKdeKchart,
      vndKdeKformula,
      vndKdeKivio,
      vndKdeKontour,
      vndKdeKpresenter,
      vndKdeKspread,
      vndKdeKword,
      vndKdl,
      vndKenameaapp,
      vndKeymanKmpZip,
      vndKeymanKmx,
      vndKidspiration,
      vndKinar,
      vndKoan,
      vndKodakDescriptor,
      vndLas,
      vndLasLasJson,
      vndLasLasXml,
      vndLaszip,
      vndLdevProductlicensing,
      vndLeapJson,
      vndLibertyRequestXml,
      vndLlamagraphicsLifeBalanceDesktop,
      vndLlamagraphicsLifeBalanceExchangeXml,
      vndLogipipeCircuitZip,
      vndLoom,
      vndLotus123,
      vndLotusApproach,
      vndLotusFreelance,
      vndLotusNotes,
      vndLotusOrganizer,
      vndLotusScreencam,
      vndLotusWordpro,
      vndMacportsPortpkg,
      vndMaml,
      vndMapboxVectorTile,
      vndMarlinDrmActiontokenXml,
      vndMarlinDrmConftokenXml,
      vndMarlinDrmLicenseXml,
      vndMarlinDrmMdcf,
      vndMasonJson,
      vndMaxarArchive3tzZip,
      vndMaxmindMaxmindDb,
      vndMcd,
      vndMdl,
      vndMdlMbsdf,
      vndMedcalcdata,
      vndMediastationCdkey,
      vndMedicalholodeckRecordxr,
      vndMeridianSlingshot,
      vndMermaid,
      vndMfer,
      vndMfmp,
      vndMicroJson,
      vndMicrografxFlo,
      vndMicrografxIgx,
      vndMicrosoftPortableExecutable,
      vndMicrosoftWindowsThumbnailCache,
      vndMieleJson,
      vndMif,
      vndMinisoftHp3000Save,
      vndMitsubishiMistyGuardTrustweb,
      vndMobiusDaf,
      vndMobiusDis,
      vndMobiusMbk,
      vndMobiusMqy,
      vndMobiusMsl,
      vndMobiusPlc,
      vndMobiusTxf,
      vndModl,
      vndMophunApplication,
      vndMophunCertificate,
      vndMotorolaFlexsuite,
      vndMotorolaFlexsuiteAdsi,
      vndMotorolaFlexsuiteFis,
      vndMotorolaFlexsuiteGotap,
      vndMotorolaFlexsuiteKmr,
      vndMotorolaFlexsuiteTtc,
      vndMotorolaFlexsuiteWem,
      vndMotorolaIprm,
      vndMozillaXulXml,
      vndMs3mfdocument,
      vndMsArtgalry,
      vndMsAsf,
      vndMsCabCompressed,
      vndMsColorIccprofile,
      vndMsExcel,
      vndMsExcelAddinMacroenabled12,
      vndMsExcelSheetBinaryMacroenabled12,
      vndMsExcelSheetMacroenabled12,
      vndMsExcelTemplateMacroenabled12,
      vndMsFontobject,
      vndMsHtmlhelp,
      vndMsIms,
      vndMsLrm,
      vndMsOfficeActivexXml,
      vndMsOfficetheme,
      vndMsOpentype,
      vndMsOutlook,
      vndMsPackageObfuscatedOpentype,
      vndMsPkiSeccat,
      vndMsPkiStl,
      vndMsPlayreadyInitiatorXml,
      vndMsPowerpoint,
      vndMsPowerpointAddinMacroenabled12,
      vndMsPowerpointPresentationMacroenabled12,
      vndMsPowerpointSlideMacroenabled12,
      vndMsPowerpointSlideshowMacroenabled12,
      vndMsPowerpointTemplateMacroenabled12,
      vndMsPrintdevicecapabilitiesXml,
      vndMsPrintingPrintticketXml,
      vndMsPrintschematicketXml,
      vndMsProject,
      vndMsTnef,
      vndMsVisioViewer,
      vndMsWindowsDevicepairing,
      vndMsWindowsNwprintingOob,
      vndMsWindowsPrinterpairing,
      vndMsWindowsWsdOob,
      vndMsWmdrmLicChlgReq,
      vndMsWmdrmLicResp,
      vndMsWmdrmMeterChlgReq,
      vndMsWmdrmMeterResp,
      vndMsWordDocumentMacroenabled12,
      vndMsWordTemplateMacroenabled12,
      vndMsWorks,
      vndMsWpl,
      vndMsXpsdocument,
      vndMsaDiskImage,
      vndMseq,
      vndMsgpack,
      vndMsign,
      vndMultiadCreator,
      vndMultiadCreatorCif,
      vndMusicNiff,
      vndMusician,
      vndMuveeStyle,
      vndMynfc,
      vndNacamarYbridJson,
      vndNatoBindingdataobjectCbor,
      vndNatoBindingdataobjectJson,
      vndNatoBindingdataobjectXml,
      vndNatoOpenxmlformatsPackageIepdZip,
      vndNcdControl,
      vndNcdReference,
      vndNearstInvJson,
      vndNebumindLine,
      vndNervana,
      vndNetfpx,
      vndNeurolanguageNlu,
      vndNimn,
      vndNintendoNitroRom,
      vndNintendoSnesRom,
      vndNitf,
      vndNoblenetDirectory,
      vndNoblenetSealer,
      vndNoblenetWeb,
      vndNokiaCatalogs,
      vndNokiaConmlWbxml,
      vndNokiaConmlXml,
      vndNokiaIptvConfigXml,
      vndNokiaIsdsRadioPresets,
      vndNokiaLandmarkWbxml,
      vndNokiaLandmarkXml,
      vndNokiaLandmarkcollectionXml,
      vndNokiaNGageAcXml,
      vndNokiaNGageData,
      vndNokiaNGageSymbianInstall,
      vndNokiaNcd,
      vndNokiaPcdWbxml,
      vndNokiaPcdXml,
      vndNokiaRadioPreset,
      vndNokiaRadioPresets,
      vndNovadigmEdm,
      vndNovadigmEdx,
      vndNovadigmExt,
      vndNttLocalContentShare,
      vndNttLocalFileTransfer,
      vndNttLocalOgwRemoteAccess,
      vndNttLocalSipTaRemote,
      vndNttLocalSipTaTcpStream,
      vndNubaltecNudokuGame,
      vndOaiWorkflows,
      vndOaiWorkflowsJson,
      vndOaiWorkflowsYaml,
      vndOasisOpendocumentBase,
      vndOasisOpendocumentChart,
      vndOasisOpendocumentChartTemplate,
      vndOasisOpendocumentDatabase,
      vndOasisOpendocumentFormula,
      vndOasisOpendocumentFormulaTemplate,
      vndOasisOpendocumentGraphics,
      vndOasisOpendocumentGraphicsTemplate,
      vndOasisOpendocumentImage,
      vndOasisOpendocumentImageTemplate,
      vndOasisOpendocumentPresentation,
      vndOasisOpendocumentPresentationTemplate,
      vndOasisOpendocumentSpreadsheet,
      vndOasisOpendocumentSpreadsheetTemplate,
      vndOasisOpendocumentText,
      vndOasisOpendocumentTextMaster,
      vndOasisOpendocumentTextMasterTemplate,
      vndOasisOpendocumentTextTemplate,
      vndOasisOpendocumentTextWeb,
      vndObn,
      vndOcfCbor,
      vndOciImageManifestV1Json,
      vndOftnL10nJson,
      vndOipfContentaccessdownloadXml,
      vndOipfContentaccessstreamingXml,
      vndOipfCspgHexbinary,
      vndOipfDaeSvgXml,
      vndOipfDaeXhtmlXml,
      vndOipfMippvcontrolmessageXml,
      vndOipfPaeGem,
      vndOipfSpdiscoveryXml,
      vndOipfSpdlistXml,
      vndOipfUeprofileXml,
      vndOipfUserprofileXml,
      vndOlpcSugar,
      vndOmaScwsConfig,
      vndOmaScwsHttpRequest,
      vndOmaScwsHttpResponse,
      vndOmaBcastAssociatedProcedureParameterXml,
      vndOmaBcastDrmTriggerXml,
      vndOmaBcastImdXml,
      vndOmaBcastLtkm,
      vndOmaBcastNotificationXml,
      vndOmaBcastProvisioningtrigger,
      vndOmaBcastSgboot,
      vndOmaBcastSgddXml,
      vndOmaBcastSgdu,
      vndOmaBcastSimpleSymbolContainer,
      vndOmaBcastSmartcardTriggerXml,
      vndOmaBcastSprovXml,
      vndOmaBcastStkm,
      vndOmaCabAddressBookXml,
      vndOmaCabFeatureHandlerXml,
      vndOmaCabPccXml,
      vndOmaCabSubsInviteXml,
      vndOmaCabUserPrefsXml,
      vndOmaDcd,
      vndOmaDcdc,
      vndOmaDd2Xml,
      vndOmaDrmRisdXml,
      vndOmaGroupUsageListXml,
      vndOmaLwm2mCbor,
      vndOmaLwm2mJson,
      vndOmaLwm2mTlv,
      vndOmaPalXml,
      vndOmaPocDetailedProgressReportXml,
      vndOmaPocFinalReportXml,
      vndOmaPocGroupsXml,
      vndOmaPocInvocationDescriptorXml,
      vndOmaPocOptimizedProgressReportXml,
      vndOmaPush,
      vndOmaScidmMessagesXml,
      vndOmaXcapDirectoryXml,
      vndOmadsEmailXml,
      vndOmadsFileXml,
      vndOmadsFolderXml,
      vndOmalocSuplInit,
      vndOmsCellularCoseContentCbor,
      vndOnepager,
      vndOnepagertamp,
      vndOnepagertamx,
      vndOnepagertat,
      vndOnepagertatp,
      vndOnepagertatx,
      vndOnvifMetadata,
      vndOpenbloxGameXml,
      vndOpenbloxGameBinary,
      vndOpeneyeOeb,
      vndOpenofficeorgExtension,
      vndOpenprinttag,
      vndOpenstreetmapDataXml,
      vndOpentimestampsOts,
      vndOpenvpiDspxJson,
      vndOpenxmlformatsOfficedocumentCustomPropertiesXml,
      vndOpenxmlformatsOfficedocumentCustomxmlpropertiesXml,
      vndOpenxmlformatsOfficedocumentDrawingXml,
      vndOpenxmlformatsOfficedocumentDrawingmlChartXml,
      vndOpenxmlformatsOfficedocumentDrawingmlChartshapesXml,
      vndOpenxmlformatsOfficedocumentDrawingmlDiagramcolorsXml,
      vndOpenxmlformatsOfficedocumentDrawingmlDiagramdataXml,
      vndOpenxmlformatsOfficedocumentDrawingmlDiagramlayoutXml,
      vndOpenxmlformatsOfficedocumentDrawingmlDiagramstyleXml,
      vndOpenxmlformatsOfficedocumentExtendedPropertiesXml,
      vndOpenxmlformatsOfficedocumentPresentationmlCommentauthorsXml,
      vndOpenxmlformatsOfficedocumentPresentationmlCommentsXml,
      vndOpenxmlformatsOfficedocumentPresentationmlHandoutmasterXml,
      vndOpenxmlformatsOfficedocumentPresentationmlNotesmasterXml,
      vndOpenxmlformatsOfficedocumentPresentationmlNotesslideXml,
      vndOpenxmlformatsOfficedocumentPresentationmlPresentation,
      vndOpenxmlformatsOfficedocumentPresentationmlPresentationMainXml,
      vndOpenxmlformatsOfficedocumentPresentationmlPrespropsXml,
      vndOpenxmlformatsOfficedocumentPresentationmlSlide,
      vndOpenxmlformatsOfficedocumentPresentationmlSlideXml,
      vndOpenxmlformatsOfficedocumentPresentationmlSlidelayoutXml,
      vndOpenxmlformatsOfficedocumentPresentationmlSlidemasterXml,
      vndOpenxmlformatsOfficedocumentPresentationmlSlideshow,
      vndOpenxmlformatsOfficedocumentPresentationmlSlideshowMainXml,
      vndOpenxmlformatsOfficedocumentPresentationmlSlideupdateinfoXml,
      vndOpenxmlformatsOfficedocumentPresentationmlTablestylesXml,
      vndOpenxmlformatsOfficedocumentPresentationmlTagsXml,
      vndOpenxmlformatsOfficedocumentPresentationmlTemplate,
      vndOpenxmlformatsOfficedocumentPresentationmlTemplateMainXml,
      vndOpenxmlformatsOfficedocumentPresentationmlViewpropsXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlCalcchainXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlChartsheetXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlCommentsXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlConnectionsXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlDialogsheetXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlExternallinkXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlPivotcachedefinitionXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlPivotcacherecordsXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlPivottableXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlQuerytableXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlRevisionheadersXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlRevisionlogXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlSharedstringsXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlSheet,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlSheetMainXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlSheetmetadataXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlStylesXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlTableXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlTablesinglecellsXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlTemplate,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlTemplateMainXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlUsernamesXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlVolatiledependenciesXml,
      vndOpenxmlformatsOfficedocumentSpreadsheetmlWorksheetXml,
      vndOpenxmlformatsOfficedocumentThemeXml,
      vndOpenxmlformatsOfficedocumentThemeoverrideXml,
      vndOpenxmlformatsOfficedocumentVmldrawing,
      vndOpenxmlformatsOfficedocumentWordprocessingmlCommentsXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlDocument,
      vndOpenxmlformatsOfficedocumentWordprocessingmlDocumentGlossaryXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlDocumentMainXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlEndnotesXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlFonttableXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlFooterXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlFootnotesXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlNumberingXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlSettingsXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlStylesXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlTemplate,
      vndOpenxmlformatsOfficedocumentWordprocessingmlTemplateMainXml,
      vndOpenxmlformatsOfficedocumentWordprocessingmlWebsettingsXml,
      vndOpenxmlformatsPackageCorePropertiesXml,
      vndOpenxmlformatsPackageDigitalSignatureXmlsignatureXml,
      vndOpenxmlformatsPackageRelationshipsXml,
      vndOracleResourceJson,
      vndOrangeIndata,
      vndOsaNetdeploy,
      vndOsgeoMapguidePackage,
      vndOsgiBundle,
      vndOsgiDp,
      vndOsgiSubsystem,
      vndOtpsCtKipXml,
      vndOxliCountgraph,
      vndPagerdutyJson,
      vndPalm,
      vndPanoply,
      vndPaosXml,
      vndPatentdive,
      vndPatientecommsdoc,
      vndPawaafile,
      vndPcos,
      vndPgFormat,
      vndPgOsasli,
      vndPiaccessApplicationLicence,
      vndPicsel,
      vndPmiWidget,
      vndPmtiles,
      vndPocGroupAdvertisementXml,
      vndPocketlearn,
      vndPowerbuilder6,
      vndPowerbuilder6S,
      vndPowerbuilder7,
      vndPowerbuilder7S,
      vndPowerbuilder75,
      vndPowerbuilder75S,
      vndPpSystemverifyXml,
      vndPreminet,
      vndPreviewsystemsBox,
      vndProcreateBrush,
      vndProcreateBrushset,
      vndProcreateDream,
      vndProjectGraph,
      vndProteusMagazine,
      vndPsfs,
      vndPtMundusmundi,
      vndPublishareDeltaTree,
      vndPviPtid1,
      vndPwgMultiplexed,
      vndPwgXhtmlPrintXml,
      vndPyonJson,
      vndQualcommBrewAppRes,
      vndQuarantainenet,
      vndQuarkQuarkxpress,
      vndQuobjectQuoxdocument,
      vndR74nSandboxelsJson,
      vndRadisysMomlXml,
      vndRadisysMsmlXml,
      vndRadisysMsmlAuditXml,
      vndRadisysMsmlAuditConfXml,
      vndRadisysMsmlAuditConnXml,
      vndRadisysMsmlAuditDialogXml,
      vndRadisysMsmlAuditStreamXml,
      vndRadisysMsmlConfXml,
      vndRadisysMsmlDialogXml,
      vndRadisysMsmlDialogBaseXml,
      vndRadisysMsmlDialogFaxDetectXml,
      vndRadisysMsmlDialogFaxSendrecvXml,
      vndRadisysMsmlDialogGroupXml,
      vndRadisysMsmlDialogSpeechXml,
      vndRadisysMsmlDialogTransformXml,
      vndRainstorData,
      vndRapid,
      vndRar,
      vndRealvncBed,
      vndRecordareMusicxml,
      vndRecordareMusicxmlXml,
      vndRelpipe,
      vndRenlearnRlprint,
      vndResilientLogic,
      vndRestfulJson,
      vndRigCryptonote,
      vndRimCod,
      vndRnRealmedia,
      vndRnRealmediaVbr,
      vndRoute66Link66Xml,
      vndRs274x,
      vndRuckusDownload,
      vndS3sms,
      vndSailingtrackerTrack,
      vndSar,
      vndSbmCid,
      vndSbmMid2,
      vndScribus,
      vndSealed3df,
      vndSealedCsf,
      vndSealedDoc,
      vndSealedEml,
      vndSealedMht,
      vndSealedNet,
      vndSealedPpt,
      vndSealedTiff,
      vndSealedXls,
      vndSealedmediaSoftsealHtml,
      vndSealedmediaSoftsealPdf,
      vndSeemail,
      vndSeisJson,
      vndSema,
      vndSemd,
      vndSemf,
      vndShadeSaveFile,
      vndShanaInformedFormdata,
      vndShanaInformedFormtemplate,
      vndShanaInformedInterchange,
      vndShanaInformedPackage,
      vndShootproofJson,
      vndShopkickJson,
      vndShp,
      vndShx,
      vndSigrokSession,
      vndSimtechMindmapper,
      vndSirenJson,
      vndSirtxVmv0,
      vndSketchometry,
      vndSmaf,
      vndSmartNotebook,
      vndSmartTeacher,
      vndSmintioPortalsArchive,
      vndSnesdevPageTable,
      vndSoftware602FillerFormXml,
      vndSoftware602FillerFormXmlZip,
      vndSolentSdkmXml,
      vndSpotfireDxp,
      vndSpotfireSfs,
      vndSqlite3,
      vndSssCod,
      vndSssDtf,
      vndSssNtf,
      vndStardivisionCalc,
      vndStardivisionDraw,
      vndStardivisionImpress,
      vndStardivisionMath,
      vndStardivisionWriter,
      vndStardivisionWriterGlobal,
      vndStepmaniaPackage,
      vndStepmaniaStepchart,
      vndStreetStream,
      vndSunWadlXml,
      vndSunXmlCalc,
      vndSunXmlCalcTemplate,
      vndSunXmlDraw,
      vndSunXmlDrawTemplate,
      vndSunXmlImpress,
      vndSunXmlImpressTemplate,
      vndSunXmlMath,
      vndSunXmlWriter,
      vndSunXmlWriterGlobal,
      vndSunXmlWriterTemplate,
      vndSuperfileSuper,
      vndSusCalendar,
      vndSvd,
      vndSwiftviewIcs,
      vndSybylMol2,
      vndSycleXml,
      vndSyftJson,
      vndSymbianInstall,
      vndSyncmlXml,
      vndSyncmlDmWbxml,
      vndSyncmlDmXml,
      vndSyncmlDmNotification,
      vndSyncmlDmddfWbxml,
      vndSyncmlDmddfXml,
      vndSyncmlDmtndsWbxml,
      vndSyncmlDmtndsXml,
      vndSyncmlDsNotification,
      vndTableschemaJson,
      vndTaoIntentModuleArchive,
      vndTcpdumpPcap,
      vndThinkCellPpttcJson,
      vndTmdMediaflexApiXml,
      vndTml,
      vndTmobileLivetv,
      vndTriOnesource,
      vndTridTpt,
      vndTriscapeMxs,
      vndTrueapp,
      vndTruedoc,
      vndUbisoftWebplayer,
      vndUfdl,
      vndUicDosipasV1,
      vndUicDosipasV2,
      vndUicOsdmJson,
      vndUicTlbFcb,
      vndUiqTheme,
      vndUmajin,
      vndUnity,
      vndUomlXml,
      vndUplanetAlert,
      vndUplanetAlertWbxml,
      vndUplanetBearerChoice,
      vndUplanetBearerChoiceWbxml,
      vndUplanetCacheop,
      vndUplanetCacheopWbxml,
      vndUplanetChannel,
      vndUplanetChannelWbxml,
      vndUplanetList,
      vndUplanetListWbxml,
      vndUplanetListcmd,
      vndUplanetListcmdWbxml,
      vndUplanetSignal,
      vndUriMap,
      vndValveSourceMaterial,
      vndVcx,
      vndVdStudy,
      vndVectorworks,
      vndVelJson,
      vndVeraisonTsmReportCbor,
      vndVeraisonTsmReportJson,
      vndVerifierAttestationJwt,
      vndVerimatrixVcas,
      vndVeritoneAionJson,
      vndVeryantThin,
      vndVesEncrypted,
      vndVidsoftVidconference,
      vndVisio,
      vndVisionary,
      vndVividenceScriptfile,
      vndVocalshaperVsp4,
      vndVsf,
      vndVuq,
      vndWantverse,
      vndWapSic,
      vndWapSlc,
      vndWapWbxml,
      vndWapWmlc,
      vndWapWmlscriptc,
      vndWasmflowWafl,
      vndWebturbo,
      vndWfaDpp,
      vndWfaP2p,
      vndWfaWsc,
      vndWindowsDevicepairing,
      vndWmap,
      vndWmc,
      vndWmfBootstrap,
      vndWolframMathematica,
      vndWolframMathematicaPackage,
      vndWolframPlayer,
      vndWordlift,
      vndWordperfect,
      vndWqd,
      vndWrqHp3000Labelled,
      vndWtStf,
      vndWvCspWbxml,
      vndWvCspXml,
      vndWvSspXml,
      vndXacmlJson,
      vndXara,
      vndXarinCpj,
      vndXcdn,
      vndXecretsEncrypted,
      vndXfdl,
      vndXfdlWebform,
      vndXmiXml,
      vndXmpieCpkg,
      vndXmpieDpkg,
      vndXmpiePlan,
      vndXmpiePpkg,
      vndXmpieXlim,
      vndYamahaHvDic,
      vndYamahaHvScript,
      vndYamahaHvVoice,
      vndYamahaOpenscoreformat,
      vndYamahaOpenscoreformatOsfpvgXml,
      vndYamahaRemoteSetup,
      vndYamahaSmafAudio,
      vndYamahaSmafPhrase,
      vndYamahaThroughNgn,
      vndYamahaTunnelUdpencap,
      vndYaoweme,
      vndYellowriverCustomMenu,
      vndZohoPresentationShow,
      vndZul,
      vndZzazzDeckXml,
      voicexmlXml,
      voucherCmsJson,
      voucherJwsJson,
      vp,
      vpCose,
      vpJwt,
      vpSdJwt,
      vqRtcpxr,
      wasm,
      watcherinfoXml,
      webpushOptionsJson,
      whoisppQuery,
      whoisppResponse,
      widget,
      winhlp,
      wita,
      wordperfect51,
      wsdlXml,
      wspolicyXml,
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
      xDtbncxXml,
      xDtbookXml,
      xDtbresourceXml,
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
      xIpynbJson,
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
      xWebAppManifestJson,
      xWwwFormUrlencoded,
      xX509CaCert,
      xX509CaRaCert,
      xX509NextCaCert,
      xXfig,
      xXliffXml,
      xXpinstall,
      xXz,
      xZipCompressed,
      xZmachine,
      x400Bp,
      xacmlXml,
      xamlXml,
      xcapAttXml,
      xcapCapsXml,
      xcapDiffXml,
      xcapElXml,
      xcapErrorXml,
      xcapNsXml,
      xconConferenceInfoXml,
      xconConferenceInfoDiffXml,
      xencXml,
      xfdf,
      xhtmlXml,
      xhtmlVoiceXml,
      xliffXml,
      xml,
      xmlDtd,
      xmlExternalParsedEntity,
      xmlPatchXml,
      xmppXml,
      xopXml,
      xprocXml,
      xsltXml,
      xspfXml,
      xvXml,
      yaml,
      yang,
      yangDataCbor,
      yangDataJson,
      yangDataXml,
      yangPatchJson,
      yangPatchXml,
      yangSidJson,
      yinXml,
      zip,
      zipDotlottie,
      zlib,
      zstd
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

    lazy val aac: MediaType =
      MediaType("audio", "aac", compressible = false, binary = true, fileExtensions = List("adts", "aac"))

    lazy val ac3: MediaType =
      MediaType("audio", "ac3", compressible = false, binary = true)

    lazy val adpcm: MediaType =
      MediaType("audio", "adpcm", compressible = false, binary = true, fileExtensions = List("adp"))

    lazy val amr: MediaType =
      MediaType("audio", "amr", compressible = false, binary = true, fileExtensions = List("amr"))

    lazy val `amr-wb`: MediaType =
      MediaType("audio", "amr-wb", compressible = false, binary = true)

    lazy val `amr-wb+`: MediaType =
      MediaType("audio", "amr-wb+", compressible = false, binary = true)

    lazy val aptx: MediaType =
      MediaType("audio", "aptx", compressible = false, binary = true)

    lazy val asc: MediaType =
      MediaType("audio", "asc", compressible = false, binary = true)

    lazy val atracAdvancedLossless: MediaType =
      MediaType("audio", "atrac-advanced-lossless", compressible = false, binary = true)
    
    lazy val `atrac-advanced-lossless`: MediaType = atracAdvancedLossless

    lazy val atracX: MediaType =
      MediaType("audio", "atrac-x", compressible = false, binary = true)
    
    lazy val `atrac-x`: MediaType = atracX

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
    
    lazy val `dsr-es201108`: MediaType = dsrEs201108

    lazy val dsrEs202050: MediaType =
      MediaType("audio", "dsr-es202050", compressible = false, binary = true)
    
    lazy val `dsr-es202050`: MediaType = dsrEs202050

    lazy val dsrEs202211: MediaType =
      MediaType("audio", "dsr-es202211", compressible = false, binary = true)
    
    lazy val `dsr-es202211`: MediaType = dsrEs202211

    lazy val dsrEs202212: MediaType =
      MediaType("audio", "dsr-es202212", compressible = false, binary = true)
    
    lazy val `dsr-es202212`: MediaType = dsrEs202212

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
    
    lazy val `evrc-qcp`: MediaType = evrcQcp

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
    
    lazy val `g711-0`: MediaType = g7110

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
    
    lazy val `g726-16`: MediaType = g72616

    lazy val g72624: MediaType =
      MediaType("audio", "g726-24", compressible = false, binary = true)
    
    lazy val `g726-24`: MediaType = g72624

    lazy val g72632: MediaType =
      MediaType("audio", "g726-32", compressible = false, binary = true)
    
    lazy val `g726-32`: MediaType = g72632

    lazy val g72640: MediaType =
      MediaType("audio", "g726-40", compressible = false, binary = true)
    
    lazy val `g726-40`: MediaType = g72640

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
    
    lazy val `gsm-efr`: MediaType = gsmEfr

    lazy val gsmHr08: MediaType =
      MediaType("audio", "gsm-hr-08", compressible = false, binary = true)
    
    lazy val `gsm-hr-08`: MediaType = gsmHr08

    lazy val ilbc: MediaType =
      MediaType("audio", "ilbc", compressible = false, binary = true)

    lazy val ipMrV25: MediaType =
      MediaType("audio", "ip-mr_v2.5", compressible = false, binary = true)
    
    lazy val `ip-mr_v2.5`: MediaType = ipMrV25

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
      MediaType("audio", "midi", compressible = false, binary = true, fileExtensions = List("mid", "midi", "kar", "rmi"))

    lazy val midiClip: MediaType =
      MediaType("audio", "midi-clip", compressible = false, binary = true)
    
    lazy val `midi-clip`: MediaType = midiClip

    lazy val mobileXmf: MediaType =
      MediaType("audio", "mobile-xmf", compressible = false, binary = true, fileExtensions = List("mxmf"))
    
    lazy val `mobile-xmf`: MediaType = mobileXmf

    lazy val mp3: MediaType =
      MediaType("audio", "mp3", compressible = false, binary = true, fileExtensions = List("mp3"))

    lazy val mp4: MediaType =
      MediaType("audio", "mp4", compressible = false, binary = true, fileExtensions = List("m4a", "mp4a", "m4b"))

    lazy val mp4aLatm: MediaType =
      MediaType("audio", "mp4a-latm", compressible = false, binary = true)
    
    lazy val `mp4a-latm`: MediaType = mp4aLatm

    lazy val mpa: MediaType =
      MediaType("audio", "mpa", compressible = false, binary = true)

    lazy val mpaRobust: MediaType =
      MediaType("audio", "mpa-robust", compressible = false, binary = true)
    
    lazy val `mpa-robust`: MediaType = mpaRobust

    lazy val mpeg: MediaType =
      MediaType("audio", "mpeg", compressible = false, binary = true, fileExtensions = List("mpga", "mp2", "mp2a", "mp3", "m2a", "m3a"))

    lazy val mpeg4Generic: MediaType =
      MediaType("audio", "mpeg4-generic", compressible = false, binary = true)
    
    lazy val `mpeg4-generic`: MediaType = mpeg4Generic

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
    
    lazy val `pcma-wb`: MediaType = pcmaWb

    lazy val pcmu: MediaType =
      MediaType("audio", "pcmu", compressible = false, binary = true)

    lazy val pcmuWb: MediaType =
      MediaType("audio", "pcmu-wb", compressible = false, binary = true)
    
    lazy val `pcmu-wb`: MediaType = pcmuWb

    lazy val prsSid: MediaType =
      MediaType("audio", "prs.sid", compressible = false, binary = true)
    
    lazy val `prs.sid`: MediaType = prsSid

    lazy val qcelp: MediaType =
      MediaType("audio", "qcelp", compressible = false, binary = true)

    lazy val raptorfec: MediaType =
      MediaType("audio", "raptorfec", compressible = false, binary = true)

    lazy val red: MediaType =
      MediaType("audio", "red", compressible = false, binary = true)

    lazy val rtpEncAescm128: MediaType =
      MediaType("audio", "rtp-enc-aescm128", compressible = false, binary = true)
    
    lazy val `rtp-enc-aescm128`: MediaType = rtpEncAescm128

    lazy val rtpMidi: MediaType =
      MediaType("audio", "rtp-midi", compressible = false, binary = true)
    
    lazy val `rtp-midi`: MediaType = rtpMidi

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
    
    lazy val `smv-qcp`: MediaType = smvQcp

    lazy val smv0: MediaType =
      MediaType("audio", "smv0", compressible = false, binary = true)

    lazy val sofa: MediaType =
      MediaType("audio", "sofa", compressible = false, binary = true)

    lazy val soundfont: MediaType =
      MediaType("audio", "soundfont", compressible = false, binary = true)

    lazy val spMidi: MediaType =
      MediaType("audio", "sp-midi", compressible = false, binary = true)
    
    lazy val `sp-midi`: MediaType = spMidi

    lazy val speex: MediaType =
      MediaType("audio", "speex", compressible = false, binary = true)

    lazy val t140c: MediaType =
      MediaType("audio", "t140c", compressible = false, binary = true)

    lazy val t38: MediaType =
      MediaType("audio", "t38", compressible = false, binary = true)

    lazy val telephoneEvent: MediaType =
      MediaType("audio", "telephone-event", compressible = false, binary = true)
    
    lazy val `telephone-event`: MediaType = telephoneEvent

    lazy val tetra_acelp: MediaType =
      MediaType("audio", "tetra_acelp", compressible = false, binary = true)

    lazy val tetra_acelp_bb: MediaType =
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
    
    lazy val `vmr-wb`: MediaType = vmrWb

    lazy val vnd3gppIufp: MediaType =
      MediaType("audio", "vnd.3gpp.iufp", compressible = false, binary = true)
    
    lazy val `vnd.3gpp.iufp`: MediaType = vnd3gppIufp

    lazy val vnd4sb: MediaType =
      MediaType("audio", "vnd.4sb", compressible = false, binary = true)
    
    lazy val `vnd.4sb`: MediaType = vnd4sb

    lazy val vndAudiokoz: MediaType =
      MediaType("audio", "vnd.audiokoz", compressible = false, binary = true)
    
    lazy val `vnd.audiokoz`: MediaType = vndAudiokoz

    lazy val vndBlockfactFacta: MediaType =
      MediaType("audio", "vnd.blockfact.facta", compressible = false, binary = true)
    
    lazy val `vnd.blockfact.facta`: MediaType = vndBlockfactFacta

    lazy val vndCelp: MediaType =
      MediaType("audio", "vnd.celp", compressible = false, binary = true)
    
    lazy val `vnd.celp`: MediaType = vndCelp

    lazy val vndCiscoNse: MediaType =
      MediaType("audio", "vnd.cisco.nse", compressible = false, binary = true)
    
    lazy val `vnd.cisco.nse`: MediaType = vndCiscoNse

    lazy val vndCmlesRadioEvents: MediaType =
      MediaType("audio", "vnd.cmles.radio-events", compressible = false, binary = true)
    
    lazy val `vnd.cmles.radio-events`: MediaType = vndCmlesRadioEvents

    lazy val vndCnsAnp1: MediaType =
      MediaType("audio", "vnd.cns.anp1", compressible = false, binary = true)
    
    lazy val `vnd.cns.anp1`: MediaType = vndCnsAnp1

    lazy val vndCnsInf1: MediaType =
      MediaType("audio", "vnd.cns.inf1", compressible = false, binary = true)
    
    lazy val `vnd.cns.inf1`: MediaType = vndCnsInf1

    lazy val vndDeceAudio: MediaType =
      MediaType("audio", "vnd.dece.audio", compressible = false, binary = true, fileExtensions = List("uva", "uvva"))
    
    lazy val `vnd.dece.audio`: MediaType = vndDeceAudio

    lazy val vndDigitalWinds: MediaType =
      MediaType("audio", "vnd.digital-winds", compressible = false, binary = true, fileExtensions = List("eol"))
    
    lazy val `vnd.digital-winds`: MediaType = vndDigitalWinds

    lazy val vndDlnaAdts: MediaType =
      MediaType("audio", "vnd.dlna.adts", compressible = false, binary = true)
    
    lazy val `vnd.dlna.adts`: MediaType = vndDlnaAdts

    lazy val vndDolbyHeaac1: MediaType =
      MediaType("audio", "vnd.dolby.heaac.1", compressible = false, binary = true)
    
    lazy val `vnd.dolby.heaac.1`: MediaType = vndDolbyHeaac1

    lazy val vndDolbyHeaac2: MediaType =
      MediaType("audio", "vnd.dolby.heaac.2", compressible = false, binary = true)
    
    lazy val `vnd.dolby.heaac.2`: MediaType = vndDolbyHeaac2

    lazy val vndDolbyMlp: MediaType =
      MediaType("audio", "vnd.dolby.mlp", compressible = false, binary = true)
    
    lazy val `vnd.dolby.mlp`: MediaType = vndDolbyMlp

    lazy val vndDolbyMps: MediaType =
      MediaType("audio", "vnd.dolby.mps", compressible = false, binary = true)
    
    lazy val `vnd.dolby.mps`: MediaType = vndDolbyMps

    lazy val vndDolbyPl2: MediaType =
      MediaType("audio", "vnd.dolby.pl2", compressible = false, binary = true)
    
    lazy val `vnd.dolby.pl2`: MediaType = vndDolbyPl2

    lazy val vndDolbyPl2x: MediaType =
      MediaType("audio", "vnd.dolby.pl2x", compressible = false, binary = true)
    
    lazy val `vnd.dolby.pl2x`: MediaType = vndDolbyPl2x

    lazy val vndDolbyPl2z: MediaType =
      MediaType("audio", "vnd.dolby.pl2z", compressible = false, binary = true)
    
    lazy val `vnd.dolby.pl2z`: MediaType = vndDolbyPl2z

    lazy val vndDolbyPulse1: MediaType =
      MediaType("audio", "vnd.dolby.pulse.1", compressible = false, binary = true)
    
    lazy val `vnd.dolby.pulse.1`: MediaType = vndDolbyPulse1

    lazy val vndDra: MediaType =
      MediaType("audio", "vnd.dra", compressible = false, binary = true, fileExtensions = List("dra"))
    
    lazy val `vnd.dra`: MediaType = vndDra

    lazy val vndDts: MediaType =
      MediaType("audio", "vnd.dts", compressible = false, binary = true, fileExtensions = List("dts"))
    
    lazy val `vnd.dts`: MediaType = vndDts

    lazy val vndDtsHd: MediaType =
      MediaType("audio", "vnd.dts.hd", compressible = false, binary = true, fileExtensions = List("dtshd"))
    
    lazy val `vnd.dts.hd`: MediaType = vndDtsHd

    lazy val vndDtsUhd: MediaType =
      MediaType("audio", "vnd.dts.uhd", compressible = false, binary = true)
    
    lazy val `vnd.dts.uhd`: MediaType = vndDtsUhd

    lazy val vndDvbFile: MediaType =
      MediaType("audio", "vnd.dvb.file", compressible = false, binary = true)
    
    lazy val `vnd.dvb.file`: MediaType = vndDvbFile

    lazy val vndEveradPlj: MediaType =
      MediaType("audio", "vnd.everad.plj", compressible = false, binary = true)
    
    lazy val `vnd.everad.plj`: MediaType = vndEveradPlj

    lazy val vndHnsAudio: MediaType =
      MediaType("audio", "vnd.hns.audio", compressible = false, binary = true)
    
    lazy val `vnd.hns.audio`: MediaType = vndHnsAudio

    lazy val vndLucentVoice: MediaType =
      MediaType("audio", "vnd.lucent.voice", compressible = false, binary = true, fileExtensions = List("lvp"))
    
    lazy val `vnd.lucent.voice`: MediaType = vndLucentVoice

    lazy val vndMsPlayreadyMediaPya: MediaType =
      MediaType("audio", "vnd.ms-playready.media.pya", compressible = false, binary = true, fileExtensions = List("pya"))
    
    lazy val `vnd.ms-playready.media.pya`: MediaType = vndMsPlayreadyMediaPya

    lazy val vndNokiaMobileXmf: MediaType =
      MediaType("audio", "vnd.nokia.mobile-xmf", compressible = false, binary = true)
    
    lazy val `vnd.nokia.mobile-xmf`: MediaType = vndNokiaMobileXmf

    lazy val vndNortelVbk: MediaType =
      MediaType("audio", "vnd.nortel.vbk", compressible = false, binary = true)
    
    lazy val `vnd.nortel.vbk`: MediaType = vndNortelVbk

    lazy val vndNueraEcelp4800: MediaType =
      MediaType("audio", "vnd.nuera.ecelp4800", compressible = false, binary = true, fileExtensions = List("ecelp4800"))
    
    lazy val `vnd.nuera.ecelp4800`: MediaType = vndNueraEcelp4800

    lazy val vndNueraEcelp7470: MediaType =
      MediaType("audio", "vnd.nuera.ecelp7470", compressible = false, binary = true, fileExtensions = List("ecelp7470"))
    
    lazy val `vnd.nuera.ecelp7470`: MediaType = vndNueraEcelp7470

    lazy val vndNueraEcelp9600: MediaType =
      MediaType("audio", "vnd.nuera.ecelp9600", compressible = false, binary = true, fileExtensions = List("ecelp9600"))
    
    lazy val `vnd.nuera.ecelp9600`: MediaType = vndNueraEcelp9600

    lazy val vndOctelSbc: MediaType =
      MediaType("audio", "vnd.octel.sbc", compressible = false, binary = true)
    
    lazy val `vnd.octel.sbc`: MediaType = vndOctelSbc

    lazy val vndPresonusMultitrack: MediaType =
      MediaType("audio", "vnd.presonus.multitrack", compressible = false, binary = true)
    
    lazy val `vnd.presonus.multitrack`: MediaType = vndPresonusMultitrack

    lazy val vndQcelp: MediaType =
      MediaType("audio", "vnd.qcelp", compressible = false, binary = true)
    
    lazy val `vnd.qcelp`: MediaType = vndQcelp

    lazy val vndRhetorex32kadpcm: MediaType =
      MediaType("audio", "vnd.rhetorex.32kadpcm", compressible = false, binary = true)
    
    lazy val `vnd.rhetorex.32kadpcm`: MediaType = vndRhetorex32kadpcm

    lazy val vndRip: MediaType =
      MediaType("audio", "vnd.rip", compressible = false, binary = true, fileExtensions = List("rip"))
    
    lazy val `vnd.rip`: MediaType = vndRip

    lazy val vndRnRealaudio: MediaType =
      MediaType("audio", "vnd.rn-realaudio", compressible = false, binary = true)
    
    lazy val `vnd.rn-realaudio`: MediaType = vndRnRealaudio

    lazy val vndSealedmediaSoftsealMpeg: MediaType =
      MediaType("audio", "vnd.sealedmedia.softseal.mpeg", compressible = false, binary = true)
    
    lazy val `vnd.sealedmedia.softseal.mpeg`: MediaType = vndSealedmediaSoftsealMpeg

    lazy val vndVmxCvsd: MediaType =
      MediaType("audio", "vnd.vmx.cvsd", compressible = false, binary = true)
    
    lazy val `vnd.vmx.cvsd`: MediaType = vndVmxCvsd

    lazy val vndWave: MediaType =
      MediaType("audio", "vnd.wave", compressible = false, binary = true)
    
    lazy val `vnd.wave`: MediaType = vndWave

    lazy val vorbis: MediaType =
      MediaType("audio", "vorbis", compressible = false, binary = true)

    lazy val vorbisConfig: MediaType =
      MediaType("audio", "vorbis-config", compressible = false, binary = true)
    
    lazy val `vorbis-config`: MediaType = vorbisConfig

    lazy val wav: MediaType =
      MediaType("audio", "wav", compressible = false, binary = true, fileExtensions = List("wav"))

    lazy val wave: MediaType =
      MediaType("audio", "wave", compressible = false, binary = true, fileExtensions = List("wav"))

    lazy val webm: MediaType =
      MediaType("audio", "webm", compressible = false, binary = true, fileExtensions = List("weba"))

    lazy val xAac: MediaType =
      MediaType("audio", "x-aac", compressible = false, binary = true, fileExtensions = List("aac"))
    
    lazy val `x-aac`: MediaType = xAac

    lazy val xAiff: MediaType =
      MediaType("audio", "x-aiff", compressible = false, binary = true, fileExtensions = List("aif", "aiff", "aifc"))
    
    lazy val `x-aiff`: MediaType = xAiff

    lazy val xCaf: MediaType =
      MediaType("audio", "x-caf", compressible = false, binary = true, fileExtensions = List("caf"))
    
    lazy val `x-caf`: MediaType = xCaf

    lazy val xFlac: MediaType =
      MediaType("audio", "x-flac", compressible = false, binary = true, fileExtensions = List("flac"))
    
    lazy val `x-flac`: MediaType = xFlac

    lazy val xM4a: MediaType =
      MediaType("audio", "x-m4a", compressible = false, binary = true, fileExtensions = List("m4a"))
    
    lazy val `x-m4a`: MediaType = xM4a

    lazy val xMatroska: MediaType =
      MediaType("audio", "x-matroska", compressible = false, binary = true, fileExtensions = List("mka"))
    
    lazy val `x-matroska`: MediaType = xMatroska

    lazy val xMpegurl: MediaType =
      MediaType("audio", "x-mpegurl", compressible = false, binary = true, fileExtensions = List("m3u"))
    
    lazy val `x-mpegurl`: MediaType = xMpegurl

    lazy val xMsWax: MediaType =
      MediaType("audio", "x-ms-wax", compressible = false, binary = true, fileExtensions = List("wax"))
    
    lazy val `x-ms-wax`: MediaType = xMsWax

    lazy val xMsWma: MediaType =
      MediaType("audio", "x-ms-wma", compressible = false, binary = true, fileExtensions = List("wma"))
    
    lazy val `x-ms-wma`: MediaType = xMsWma

    lazy val xPnRealaudio: MediaType =
      MediaType("audio", "x-pn-realaudio", compressible = false, binary = true, fileExtensions = List("ram", "ra"))
    
    lazy val `x-pn-realaudio`: MediaType = xPnRealaudio

    lazy val xPnRealaudioPlugin: MediaType =
      MediaType("audio", "x-pn-realaudio-plugin", compressible = false, binary = true, fileExtensions = List("rmp"))
    
    lazy val `x-pn-realaudio-plugin`: MediaType = xPnRealaudioPlugin

    lazy val xRealaudio: MediaType =
      MediaType("audio", "x-realaudio", compressible = false, binary = true, fileExtensions = List("ra"))
    
    lazy val `x-realaudio`: MediaType = xRealaudio

    lazy val xTta: MediaType =
      MediaType("audio", "x-tta", compressible = false, binary = true)
    
    lazy val `x-tta`: MediaType = xTta

    lazy val xWav: MediaType =
      MediaType("audio", "x-wav", compressible = false, binary = true, fileExtensions = List("wav"))
    
    lazy val `x-wav`: MediaType = xWav

    lazy val xm: MediaType =
      MediaType("audio", "xm", compressible = false, binary = true, fileExtensions = List("xm"))

    lazy val any: MediaType = MediaType("audio", "*")

    lazy val all: List[MediaType] = List(
      `1d-interleaved-parityfec`,
      `32kadpcm`,
      `3gpp`,
      `3gpp2`,
      aac,
      ac3,
      adpcm,
      amr,
      `amr-wb`,
      `amr-wb+`,
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
      ipMrV25,
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
      prsSid,
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
      soundfont,
      spMidi,
      speex,
      t140c,
      t38,
      telephoneEvent,
      tetra_acelp,
      tetra_acelp_bb,
      tone,
      tsvcis,
      uemclip,
      ulpfec,
      usac,
      vdvi,
      vmrWb,
      vnd3gppIufp,
      vnd4sb,
      vndAudiokoz,
      vndBlockfactFacta,
      vndCelp,
      vndCiscoNse,
      vndCmlesRadioEvents,
      vndCnsAnp1,
      vndCnsInf1,
      vndDeceAudio,
      vndDigitalWinds,
      vndDlnaAdts,
      vndDolbyHeaac1,
      vndDolbyHeaac2,
      vndDolbyMlp,
      vndDolbyMps,
      vndDolbyPl2,
      vndDolbyPl2x,
      vndDolbyPl2z,
      vndDolbyPulse1,
      vndDra,
      vndDts,
      vndDtsHd,
      vndDtsUhd,
      vndDvbFile,
      vndEveradPlj,
      vndHnsAudio,
      vndLucentVoice,
      vndMsPlayreadyMediaPya,
      vndNokiaMobileXmf,
      vndNortelVbk,
      vndNueraEcelp4800,
      vndNueraEcelp7470,
      vndNueraEcelp9600,
      vndOctelSbc,
      vndPresonusMultitrack,
      vndQcelp,
      vndRhetorex32kadpcm,
      vndRip,
      vndRnRealaudio,
      vndSealedmediaSoftsealMpeg,
      vndVmxCvsd,
      vndWave,
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
    
    lazy val `x-cdx`: MediaType = xCdx

    lazy val xCif: MediaType =
      MediaType("chemical", "x-cif", compressible = false, binary = true, fileExtensions = List("cif"))
    
    lazy val `x-cif`: MediaType = xCif

    lazy val xCmdf: MediaType =
      MediaType("chemical", "x-cmdf", compressible = false, binary = true, fileExtensions = List("cmdf"))
    
    lazy val `x-cmdf`: MediaType = xCmdf

    lazy val xCml: MediaType =
      MediaType("chemical", "x-cml", compressible = false, binary = true, fileExtensions = List("cml"))
    
    lazy val `x-cml`: MediaType = xCml

    lazy val xCsml: MediaType =
      MediaType("chemical", "x-csml", compressible = false, binary = true, fileExtensions = List("csml"))
    
    lazy val `x-csml`: MediaType = xCsml

    lazy val xPdb: MediaType =
      MediaType("chemical", "x-pdb", compressible = false, binary = true)
    
    lazy val `x-pdb`: MediaType = xPdb

    lazy val xXyz: MediaType =
      MediaType("chemical", "x-xyz", compressible = false, binary = true, fileExtensions = List("xyz"))
    
    lazy val `x-xyz`: MediaType = xXyz

    lazy val any: MediaType = MediaType("chemical", "*")

    lazy val all: List[MediaType] = List(
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

    lazy val any: MediaType = MediaType("font", "*")

    lazy val all: List[MediaType] = List(
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
    
    lazy val `dicom-rle`: MediaType = dicomRle

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
    
    lazy val `heic-sequence`: MediaType = heicSequence

    lazy val heif: MediaType =
      MediaType("image", "heif", compressible = false, binary = true, fileExtensions = List("heif"))

    lazy val heifSequence: MediaType =
      MediaType("image", "heif-sequence", compressible = false, binary = true, fileExtensions = List("heifs"))
    
    lazy val `heif-sequence`: MediaType = heifSequence

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

    lazy val prsBtif: MediaType =
      MediaType("image", "prs.btif", compressible = false, binary = true, fileExtensions = List("btif", "btf"))
    
    lazy val `prs.btif`: MediaType = prsBtif

    lazy val prsPti: MediaType =
      MediaType("image", "prs.pti", compressible = false, binary = true, fileExtensions = List("pti"))
    
    lazy val `prs.pti`: MediaType = prsPti

    lazy val pwgRaster: MediaType =
      MediaType("image", "pwg-raster", compressible = false, binary = true)
    
    lazy val `pwg-raster`: MediaType = pwgRaster

    lazy val sgi: MediaType =
      MediaType("image", "sgi", compressible = false, binary = true, fileExtensions = List("sgi"))

    lazy val svgXml: MediaType =
      MediaType("image", "svg+xml", compressible = true, binary = true, fileExtensions = List("svg", "svgz"))
    
    lazy val `svg+xml`: MediaType = svgXml

    lazy val t38: MediaType =
      MediaType("image", "t38", compressible = false, binary = true, fileExtensions = List("t38"))

    lazy val tiff: MediaType =
      MediaType("image", "tiff", compressible = false, binary = true, fileExtensions = List("tif", "tiff"))

    lazy val tiffFx: MediaType =
      MediaType("image", "tiff-fx", compressible = false, binary = true, fileExtensions = List("tfx"))
    
    lazy val `tiff-fx`: MediaType = tiffFx

    lazy val vndAdobePhotoshop: MediaType =
      MediaType("image", "vnd.adobe.photoshop", compressible = true, binary = true, fileExtensions = List("psd"))
    
    lazy val `vnd.adobe.photoshop`: MediaType = vndAdobePhotoshop

    lazy val vndAirzipAcceleratorAzv: MediaType =
      MediaType("image", "vnd.airzip.accelerator.azv", compressible = false, binary = true, fileExtensions = List("azv"))
    
    lazy val `vnd.airzip.accelerator.azv`: MediaType = vndAirzipAcceleratorAzv

    lazy val vndBlockfactFacti: MediaType =
      MediaType("image", "vnd.blockfact.facti", compressible = false, binary = true, fileExtensions = List("facti"))
    
    lazy val `vnd.blockfact.facti`: MediaType = vndBlockfactFacti

    lazy val vndClip: MediaType =
      MediaType("image", "vnd.clip", compressible = false, binary = true)
    
    lazy val `vnd.clip`: MediaType = vndClip

    lazy val vndCnsInf2: MediaType =
      MediaType("image", "vnd.cns.inf2", compressible = false, binary = true)
    
    lazy val `vnd.cns.inf2`: MediaType = vndCnsInf2

    lazy val vndDeceGraphic: MediaType =
      MediaType("image", "vnd.dece.graphic", compressible = false, binary = true, fileExtensions = List("uvi", "uvvi", "uvg", "uvvg"))
    
    lazy val `vnd.dece.graphic`: MediaType = vndDeceGraphic

    lazy val vndDjvu: MediaType =
      MediaType("image", "vnd.djvu", compressible = false, binary = true, fileExtensions = List("djvu", "djv"))
    
    lazy val `vnd.djvu`: MediaType = vndDjvu

    lazy val vndDvbSubtitle: MediaType =
      MediaType("image", "vnd.dvb.subtitle", compressible = false, binary = true, fileExtensions = List("sub"))
    
    lazy val `vnd.dvb.subtitle`: MediaType = vndDvbSubtitle

    lazy val vndDwg: MediaType =
      MediaType("image", "vnd.dwg", compressible = false, binary = true, fileExtensions = List("dwg"))
    
    lazy val `vnd.dwg`: MediaType = vndDwg

    lazy val vndDxf: MediaType =
      MediaType("image", "vnd.dxf", compressible = false, binary = true, fileExtensions = List("dxf"))
    
    lazy val `vnd.dxf`: MediaType = vndDxf

    lazy val vndFastbidsheet: MediaType =
      MediaType("image", "vnd.fastbidsheet", compressible = false, binary = true, fileExtensions = List("fbs"))
    
    lazy val `vnd.fastbidsheet`: MediaType = vndFastbidsheet

    lazy val vndFpx: MediaType =
      MediaType("image", "vnd.fpx", compressible = false, binary = true, fileExtensions = List("fpx"))
    
    lazy val `vnd.fpx`: MediaType = vndFpx

    lazy val vndFst: MediaType =
      MediaType("image", "vnd.fst", compressible = false, binary = true, fileExtensions = List("fst"))
    
    lazy val `vnd.fst`: MediaType = vndFst

    lazy val vndFujixeroxEdmicsMmr: MediaType =
      MediaType("image", "vnd.fujixerox.edmics-mmr", compressible = false, binary = true, fileExtensions = List("mmr"))
    
    lazy val `vnd.fujixerox.edmics-mmr`: MediaType = vndFujixeroxEdmicsMmr

    lazy val vndFujixeroxEdmicsRlc: MediaType =
      MediaType("image", "vnd.fujixerox.edmics-rlc", compressible = false, binary = true, fileExtensions = List("rlc"))
    
    lazy val `vnd.fujixerox.edmics-rlc`: MediaType = vndFujixeroxEdmicsRlc

    lazy val vndGlobalgraphicsPgb: MediaType =
      MediaType("image", "vnd.globalgraphics.pgb", compressible = false, binary = true)
    
    lazy val `vnd.globalgraphics.pgb`: MediaType = vndGlobalgraphicsPgb

    lazy val vndMicrosoftIcon: MediaType =
      MediaType("image", "vnd.microsoft.icon", compressible = true, binary = true, fileExtensions = List("ico"))
    
    lazy val `vnd.microsoft.icon`: MediaType = vndMicrosoftIcon

    lazy val vndMix: MediaType =
      MediaType("image", "vnd.mix", compressible = false, binary = true)
    
    lazy val `vnd.mix`: MediaType = vndMix

    lazy val vndMozillaApng: MediaType =
      MediaType("image", "vnd.mozilla.apng", compressible = false, binary = true)
    
    lazy val `vnd.mozilla.apng`: MediaType = vndMozillaApng

    lazy val vndMsDds: MediaType =
      MediaType("image", "vnd.ms-dds", compressible = true, binary = true, fileExtensions = List("dds"))
    
    lazy val `vnd.ms-dds`: MediaType = vndMsDds

    lazy val vndMsModi: MediaType =
      MediaType("image", "vnd.ms-modi", compressible = false, binary = true, fileExtensions = List("mdi"))
    
    lazy val `vnd.ms-modi`: MediaType = vndMsModi

    lazy val vndMsPhoto: MediaType =
      MediaType("image", "vnd.ms-photo", compressible = false, binary = true, fileExtensions = List("wdp"))
    
    lazy val `vnd.ms-photo`: MediaType = vndMsPhoto

    lazy val vndNetFpx: MediaType =
      MediaType("image", "vnd.net-fpx", compressible = false, binary = true, fileExtensions = List("npx"))
    
    lazy val `vnd.net-fpx`: MediaType = vndNetFpx

    lazy val vndPcoB16: MediaType =
      MediaType("image", "vnd.pco.b16", compressible = false, binary = true, fileExtensions = List("b16"))
    
    lazy val `vnd.pco.b16`: MediaType = vndPcoB16

    lazy val vndRadiance: MediaType =
      MediaType("image", "vnd.radiance", compressible = false, binary = true)
    
    lazy val `vnd.radiance`: MediaType = vndRadiance

    lazy val vndSealedPng: MediaType =
      MediaType("image", "vnd.sealed.png", compressible = false, binary = true)
    
    lazy val `vnd.sealed.png`: MediaType = vndSealedPng

    lazy val vndSealedmediaSoftsealGif: MediaType =
      MediaType("image", "vnd.sealedmedia.softseal.gif", compressible = false, binary = true)
    
    lazy val `vnd.sealedmedia.softseal.gif`: MediaType = vndSealedmediaSoftsealGif

    lazy val vndSealedmediaSoftsealJpg: MediaType =
      MediaType("image", "vnd.sealedmedia.softseal.jpg", compressible = false, binary = true)
    
    lazy val `vnd.sealedmedia.softseal.jpg`: MediaType = vndSealedmediaSoftsealJpg

    lazy val vndSvf: MediaType =
      MediaType("image", "vnd.svf", compressible = false, binary = true)
    
    lazy val `vnd.svf`: MediaType = vndSvf

    lazy val vndTencentTap: MediaType =
      MediaType("image", "vnd.tencent.tap", compressible = false, binary = true, fileExtensions = List("tap"))
    
    lazy val `vnd.tencent.tap`: MediaType = vndTencentTap

    lazy val vndValveSourceTexture: MediaType =
      MediaType("image", "vnd.valve.source.texture", compressible = false, binary = true, fileExtensions = List("vtf"))
    
    lazy val `vnd.valve.source.texture`: MediaType = vndValveSourceTexture

    lazy val vndWapWbmp: MediaType =
      MediaType("image", "vnd.wap.wbmp", compressible = false, binary = true, fileExtensions = List("wbmp"))
    
    lazy val `vnd.wap.wbmp`: MediaType = vndWapWbmp

    lazy val vndXiff: MediaType =
      MediaType("image", "vnd.xiff", compressible = false, binary = true, fileExtensions = List("xif"))
    
    lazy val `vnd.xiff`: MediaType = vndXiff

    lazy val vndZbrushPcx: MediaType =
      MediaType("image", "vnd.zbrush.pcx", compressible = false, binary = true, fileExtensions = List("pcx"))
    
    lazy val `vnd.zbrush.pcx`: MediaType = vndZbrushPcx

    lazy val webp: MediaType =
      MediaType("image", "webp", compressible = false, binary = true, fileExtensions = List("webp"))

    lazy val wmf: MediaType =
      MediaType("image", "wmf", compressible = false, binary = true, fileExtensions = List("wmf"))

    lazy val x3ds: MediaType =
      MediaType("image", "x-3ds", compressible = false, binary = true, fileExtensions = List("3ds"))
    
    lazy val `x-3ds`: MediaType = x3ds

    lazy val xAdobeDng: MediaType =
      MediaType("image", "x-adobe-dng", compressible = false, binary = true, fileExtensions = List("dng"))
    
    lazy val `x-adobe-dng`: MediaType = xAdobeDng

    lazy val xCmuRaster: MediaType =
      MediaType("image", "x-cmu-raster", compressible = false, binary = true, fileExtensions = List("ras"))
    
    lazy val `x-cmu-raster`: MediaType = xCmuRaster

    lazy val xCmx: MediaType =
      MediaType("image", "x-cmx", compressible = false, binary = true, fileExtensions = List("cmx"))
    
    lazy val `x-cmx`: MediaType = xCmx

    lazy val xEmf: MediaType =
      MediaType("image", "x-emf", compressible = false, binary = true)
    
    lazy val `x-emf`: MediaType = xEmf

    lazy val xFreehand: MediaType =
      MediaType("image", "x-freehand", compressible = false, binary = true, fileExtensions = List("fh", "fhc", "fh4", "fh5", "fh7"))
    
    lazy val `x-freehand`: MediaType = xFreehand

    lazy val xIcon: MediaType =
      MediaType("image", "x-icon", compressible = true, binary = true, fileExtensions = List("ico"))
    
    lazy val `x-icon`: MediaType = xIcon

    lazy val xJng: MediaType =
      MediaType("image", "x-jng", compressible = false, binary = true, fileExtensions = List("jng"))
    
    lazy val `x-jng`: MediaType = xJng

    lazy val xMrsidImage: MediaType =
      MediaType("image", "x-mrsid-image", compressible = false, binary = true, fileExtensions = List("sid"))
    
    lazy val `x-mrsid-image`: MediaType = xMrsidImage

    lazy val xMsBmp: MediaType =
      MediaType("image", "x-ms-bmp", compressible = true, binary = true, fileExtensions = List("bmp"))
    
    lazy val `x-ms-bmp`: MediaType = xMsBmp

    lazy val xPcx: MediaType =
      MediaType("image", "x-pcx", compressible = false, binary = true, fileExtensions = List("pcx"))
    
    lazy val `x-pcx`: MediaType = xPcx

    lazy val xPict: MediaType =
      MediaType("image", "x-pict", compressible = false, binary = true, fileExtensions = List("pic", "pct"))
    
    lazy val `x-pict`: MediaType = xPict

    lazy val xPortableAnymap: MediaType =
      MediaType("image", "x-portable-anymap", compressible = false, binary = true, fileExtensions = List("pnm"))
    
    lazy val `x-portable-anymap`: MediaType = xPortableAnymap

    lazy val xPortableBitmap: MediaType =
      MediaType("image", "x-portable-bitmap", compressible = false, binary = true, fileExtensions = List("pbm"))
    
    lazy val `x-portable-bitmap`: MediaType = xPortableBitmap

    lazy val xPortableGraymap: MediaType =
      MediaType("image", "x-portable-graymap", compressible = false, binary = true, fileExtensions = List("pgm"))
    
    lazy val `x-portable-graymap`: MediaType = xPortableGraymap

    lazy val xPortablePixmap: MediaType =
      MediaType("image", "x-portable-pixmap", compressible = false, binary = true, fileExtensions = List("ppm"))
    
    lazy val `x-portable-pixmap`: MediaType = xPortablePixmap

    lazy val xRgb: MediaType =
      MediaType("image", "x-rgb", compressible = false, binary = true, fileExtensions = List("rgb"))
    
    lazy val `x-rgb`: MediaType = xRgb

    lazy val xTga: MediaType =
      MediaType("image", "x-tga", compressible = false, binary = true, fileExtensions = List("tga"))
    
    lazy val `x-tga`: MediaType = xTga

    lazy val xWmf: MediaType =
      MediaType("image", "x-wmf", compressible = false, binary = true)
    
    lazy val `x-wmf`: MediaType = xWmf

    lazy val xXbitmap: MediaType =
      MediaType("image", "x-xbitmap", compressible = false, binary = true, fileExtensions = List("xbm"))
    
    lazy val `x-xbitmap`: MediaType = xXbitmap

    lazy val xXcf: MediaType =
      MediaType("image", "x-xcf", compressible = false, binary = true)
    
    lazy val `x-xcf`: MediaType = xXcf

    lazy val xXpixmap: MediaType =
      MediaType("image", "x-xpixmap", compressible = false, binary = true, fileExtensions = List("xpm"))
    
    lazy val `x-xpixmap`: MediaType = xXpixmap

    lazy val xXwindowdump: MediaType =
      MediaType("image", "x-xwindowdump", compressible = false, binary = true, fileExtensions = List("xwd"))
    
    lazy val `x-xwindowdump`: MediaType = xXwindowdump

    lazy val any: MediaType = MediaType("image", "*")

    lazy val all: List[MediaType] = List(
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
      prsBtif,
      prsPti,
      pwgRaster,
      sgi,
      svgXml,
      t38,
      tiff,
      tiffFx,
      vndAdobePhotoshop,
      vndAirzipAcceleratorAzv,
      vndBlockfactFacti,
      vndClip,
      vndCnsInf2,
      vndDeceGraphic,
      vndDjvu,
      vndDvbSubtitle,
      vndDwg,
      vndDxf,
      vndFastbidsheet,
      vndFpx,
      vndFst,
      vndFujixeroxEdmicsMmr,
      vndFujixeroxEdmicsRlc,
      vndGlobalgraphicsPgb,
      vndMicrosoftIcon,
      vndMix,
      vndMozillaApng,
      vndMsDds,
      vndMsModi,
      vndMsPhoto,
      vndNetFpx,
      vndPcoB16,
      vndRadiance,
      vndSealedPng,
      vndSealedmediaSoftsealGif,
      vndSealedmediaSoftsealJpg,
      vndSvf,
      vndTencentTap,
      vndValveSourceTexture,
      vndWapWbmp,
      vndXiff,
      vndZbrushPcx,
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
    
    lazy val `delivery-status`: MediaType = deliveryStatus

    lazy val dispositionNotification: MediaType =
      MediaType("message", "disposition-notification", compressible = false, binary = true)
    
    lazy val `disposition-notification`: MediaType = dispositionNotification

    lazy val externalBody: MediaType =
      MediaType("message", "external-body", compressible = false, binary = true)
    
    lazy val `external-body`: MediaType = externalBody

    lazy val feedbackReport: MediaType =
      MediaType("message", "feedback-report", compressible = false, binary = true)
    
    lazy val `feedback-report`: MediaType = feedbackReport

    lazy val global: MediaType =
      MediaType("message", "global", compressible = false, binary = true, fileExtensions = List("u8msg"))

    lazy val globalDeliveryStatus: MediaType =
      MediaType("message", "global-delivery-status", compressible = false, binary = true, fileExtensions = List("u8dsn"))
    
    lazy val `global-delivery-status`: MediaType = globalDeliveryStatus

    lazy val globalDispositionNotification: MediaType =
      MediaType("message", "global-disposition-notification", compressible = false, binary = true, fileExtensions = List("u8mdn"))
    
    lazy val `global-disposition-notification`: MediaType = globalDispositionNotification

    lazy val globalHeaders: MediaType =
      MediaType("message", "global-headers", compressible = false, binary = true, fileExtensions = List("u8hdr"))
    
    lazy val `global-headers`: MediaType = globalHeaders

    lazy val http: MediaType =
      MediaType("message", "http", compressible = false, binary = true)

    lazy val imdnXml: MediaType =
      MediaType("message", "imdn+xml", compressible = true, binary = false)
    
    lazy val `imdn+xml`: MediaType = imdnXml

    lazy val mls: MediaType =
      MediaType("message", "mls", compressible = false, binary = true)

    lazy val news: MediaType =
      MediaType("message", "news", compressible = false, binary = true)

    lazy val ohttpReq: MediaType =
      MediaType("message", "ohttp-req", compressible = false, binary = true)
    
    lazy val `ohttp-req`: MediaType = ohttpReq

    lazy val ohttpRes: MediaType =
      MediaType("message", "ohttp-res", compressible = false, binary = true)
    
    lazy val `ohttp-res`: MediaType = ohttpRes

    lazy val partial: MediaType =
      MediaType("message", "partial", compressible = false, binary = true)

    lazy val rfc822: MediaType =
      MediaType("message", "rfc822", compressible = true, binary = false, fileExtensions = List("eml", "mime", "mht", "mhtml"))

    lazy val sHttp: MediaType =
      MediaType("message", "s-http", compressible = false, binary = true)
    
    lazy val `s-http`: MediaType = sHttp

    lazy val sip: MediaType =
      MediaType("message", "sip", compressible = false, binary = true)

    lazy val sipfrag: MediaType =
      MediaType("message", "sipfrag", compressible = false, binary = true)

    lazy val trackingStatus: MediaType =
      MediaType("message", "tracking-status", compressible = false, binary = true)
    
    lazy val `tracking-status`: MediaType = trackingStatus

    lazy val vndSiSimp: MediaType =
      MediaType("message", "vnd.si.simp", compressible = false, binary = true)
    
    lazy val `vnd.si.simp`: MediaType = vndSiSimp

    lazy val vndWfaWsc: MediaType =
      MediaType("message", "vnd.wfa.wsc", compressible = false, binary = true, fileExtensions = List("wsc"))
    
    lazy val `vnd.wfa.wsc`: MediaType = vndWfaWsc

    lazy val any: MediaType = MediaType("message", "*")

    lazy val all: List[MediaType] = List(
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
      imdnXml,
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
      vndSiSimp,
      vndWfaWsc
    )
  }

  object model {
    lazy val `3mf`: MediaType =
      MediaType("model", "3mf", compressible = false, binary = true, fileExtensions = List("3mf"))

    lazy val e57: MediaType =
      MediaType("model", "e57", compressible = false, binary = true)

    lazy val gltfJson: MediaType =
      MediaType("model", "gltf+json", compressible = true, binary = true, fileExtensions = List("gltf"))
    
    lazy val `gltf+json`: MediaType = gltfJson

    lazy val gltfBinary: MediaType =
      MediaType("model", "gltf-binary", compressible = true, binary = true, fileExtensions = List("glb"))
    
    lazy val `gltf-binary`: MediaType = gltfBinary

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
      MediaType("model", "step", compressible = false, binary = true, fileExtensions = List("step", "stp", "stpnc", "p21", "210"))

    lazy val stepXml: MediaType =
      MediaType("model", "step+xml", compressible = true, binary = true, fileExtensions = List("stpx"))
    
    lazy val `step+xml`: MediaType = stepXml

    lazy val stepZip: MediaType =
      MediaType("model", "step+zip", compressible = false, binary = true, fileExtensions = List("stpz"))
    
    lazy val `step+zip`: MediaType = stepZip

    lazy val stepXmlZip: MediaType =
      MediaType("model", "step-xml+zip", compressible = false, binary = true, fileExtensions = List("stpxz"))
    
    lazy val `step-xml+zip`: MediaType = stepXmlZip

    lazy val stl: MediaType =
      MediaType("model", "stl", compressible = false, binary = true, fileExtensions = List("stl"))

    lazy val u3d: MediaType =
      MediaType("model", "u3d", compressible = false, binary = true, fileExtensions = List("u3d"))

    lazy val vndBary: MediaType =
      MediaType("model", "vnd.bary", compressible = false, binary = true, fileExtensions = List("bary"))
    
    lazy val `vnd.bary`: MediaType = vndBary

    lazy val vndCld: MediaType =
      MediaType("model", "vnd.cld", compressible = false, binary = true, fileExtensions = List("cld"))
    
    lazy val `vnd.cld`: MediaType = vndCld

    lazy val vndColladaXml: MediaType =
      MediaType("model", "vnd.collada+xml", compressible = true, binary = true, fileExtensions = List("dae"))
    
    lazy val `vnd.collada+xml`: MediaType = vndColladaXml

    lazy val vndDwf: MediaType =
      MediaType("model", "vnd.dwf", compressible = false, binary = true, fileExtensions = List("dwf"))
    
    lazy val `vnd.dwf`: MediaType = vndDwf

    lazy val vndFlatland3dml: MediaType =
      MediaType("model", "vnd.flatland.3dml", compressible = false, binary = true)
    
    lazy val `vnd.flatland.3dml`: MediaType = vndFlatland3dml

    lazy val vndGdl: MediaType =
      MediaType("model", "vnd.gdl", compressible = false, binary = true, fileExtensions = List("gdl"))
    
    lazy val `vnd.gdl`: MediaType = vndGdl

    lazy val `vnd.gs-gdl`: MediaType =
      MediaType("model", "vnd.gs-gdl", compressible = false, binary = true)

    lazy val `vnd.gs.gdl`: MediaType =
      MediaType("model", "vnd.gs.gdl", compressible = false, binary = true)

    lazy val vndGtw: MediaType =
      MediaType("model", "vnd.gtw", compressible = false, binary = true, fileExtensions = List("gtw"))
    
    lazy val `vnd.gtw`: MediaType = vndGtw

    lazy val vndMomlXml: MediaType =
      MediaType("model", "vnd.moml+xml", compressible = true, binary = true)
    
    lazy val `vnd.moml+xml`: MediaType = vndMomlXml

    lazy val vndMts: MediaType =
      MediaType("model", "vnd.mts", compressible = false, binary = true, fileExtensions = List("mts"))
    
    lazy val `vnd.mts`: MediaType = vndMts

    lazy val vndOpengex: MediaType =
      MediaType("model", "vnd.opengex", compressible = false, binary = true, fileExtensions = List("ogex"))
    
    lazy val `vnd.opengex`: MediaType = vndOpengex

    lazy val vndParasolidTransmitBinary: MediaType =
      MediaType("model", "vnd.parasolid.transmit.binary", compressible = false, binary = true, fileExtensions = List("x_b"))
    
    lazy val `vnd.parasolid.transmit.binary`: MediaType = vndParasolidTransmitBinary

    lazy val vndParasolidTransmitText: MediaType =
      MediaType("model", "vnd.parasolid.transmit.text", compressible = false, binary = true, fileExtensions = List("x_t"))
    
    lazy val `vnd.parasolid.transmit.text`: MediaType = vndParasolidTransmitText

    lazy val vndPythaPyox: MediaType =
      MediaType("model", "vnd.pytha.pyox", compressible = false, binary = true, fileExtensions = List("pyo", "pyox"))
    
    lazy val `vnd.pytha.pyox`: MediaType = vndPythaPyox

    lazy val vndRosetteAnnotatedDataModel: MediaType =
      MediaType("model", "vnd.rosette.annotated-data-model", compressible = false, binary = true)
    
    lazy val `vnd.rosette.annotated-data-model`: MediaType = vndRosetteAnnotatedDataModel

    lazy val vndSapVds: MediaType =
      MediaType("model", "vnd.sap.vds", compressible = false, binary = true, fileExtensions = List("vds"))
    
    lazy val `vnd.sap.vds`: MediaType = vndSapVds

    lazy val vndUsda: MediaType =
      MediaType("model", "vnd.usda", compressible = false, binary = true, fileExtensions = List("usda"))
    
    lazy val `vnd.usda`: MediaType = vndUsda

    lazy val vndUsdzZip: MediaType =
      MediaType("model", "vnd.usdz+zip", compressible = false, binary = true, fileExtensions = List("usdz"))
    
    lazy val `vnd.usdz+zip`: MediaType = vndUsdzZip

    lazy val vndValveSourceCompiledMap: MediaType =
      MediaType("model", "vnd.valve.source.compiled-map", compressible = false, binary = true, fileExtensions = List("bsp"))
    
    lazy val `vnd.valve.source.compiled-map`: MediaType = vndValveSourceCompiledMap

    lazy val vndVtu: MediaType =
      MediaType("model", "vnd.vtu", compressible = false, binary = true, fileExtensions = List("vtu"))
    
    lazy val `vnd.vtu`: MediaType = vndVtu

    lazy val vrml: MediaType =
      MediaType("model", "vrml", compressible = false, binary = true, fileExtensions = List("wrl", "vrml"))

    lazy val x3dBinary: MediaType =
      MediaType("model", "x3d+binary", compressible = false, binary = true, fileExtensions = List("x3db", "x3dbz"))
    
    lazy val `x3d+binary`: MediaType = x3dBinary

    lazy val x3dFastinfoset: MediaType =
      MediaType("model", "x3d+fastinfoset", compressible = false, binary = true, fileExtensions = List("x3db"))
    
    lazy val `x3d+fastinfoset`: MediaType = x3dFastinfoset

    lazy val `x3d+vrml`: MediaType =
      MediaType("model", "x3d+vrml", compressible = false, binary = true, fileExtensions = List("x3dv", "x3dvz"))

    lazy val x3dXml: MediaType =
      MediaType("model", "x3d+xml", compressible = true, binary = true, fileExtensions = List("x3d", "x3dz"))
    
    lazy val `x3d+xml`: MediaType = x3dXml

    lazy val `x3d-vrml`: MediaType =
      MediaType("model", "x3d-vrml", compressible = false, binary = true, fileExtensions = List("x3dv"))

    lazy val any: MediaType = MediaType("model", "*")

    lazy val all: List[MediaType] = List(
      `3mf`,
      e57,
      gltfJson,
      gltfBinary,
      iges,
      jt,
      mesh,
      mtl,
      obj,
      prc,
      step,
      stepXml,
      stepZip,
      stepXmlZip,
      stl,
      u3d,
      vndBary,
      vndCld,
      vndColladaXml,
      vndDwf,
      vndFlatland3dml,
      vndGdl,
      `vnd.gs-gdl`,
      `vnd.gs.gdl`,
      vndGtw,
      vndMomlXml,
      vndMts,
      vndOpengex,
      vndParasolidTransmitBinary,
      vndParasolidTransmitText,
      vndPythaPyox,
      vndRosetteAnnotatedDataModel,
      vndSapVds,
      vndUsda,
      vndUsdzZip,
      vndValveSourceCompiledMap,
      vndVtu,
      vrml,
      x3dBinary,
      x3dFastinfoset,
      `x3d+vrml`,
      x3dXml,
      `x3d-vrml`
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
    
    lazy val `form-data`: MediaType = formData

    lazy val headerSet: MediaType =
      MediaType("multipart", "header-set", compressible = false, binary = true)
    
    lazy val `header-set`: MediaType = headerSet

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

    lazy val vndBintMedPlus: MediaType =
      MediaType("multipart", "vnd.bint.med-plus", compressible = false, binary = true)
    
    lazy val `vnd.bint.med-plus`: MediaType = vndBintMedPlus

    lazy val voiceMessage: MediaType =
      MediaType("multipart", "voice-message", compressible = false, binary = true)
    
    lazy val `voice-message`: MediaType = voiceMessage

    lazy val xMixedReplace: MediaType =
      MediaType("multipart", "x-mixed-replace", compressible = false, binary = true)
    
    lazy val `x-mixed-replace`: MediaType = xMixedReplace

    lazy val any: MediaType = MediaType("multipart", "*")

    lazy val all: List[MediaType] = List(
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
      vndBintMedPlus,
      voiceMessage,
      xMixedReplace
    )
  }

  object text {
    lazy val `1d-interleaved-parityfec`: MediaType =
      MediaType("text", "1d-interleaved-parityfec", compressible = false, binary = false)

    lazy val cacheManifest: MediaType =
      MediaType("text", "cache-manifest", compressible = true, binary = false, fileExtensions = List("appcache", "manifest"))
    
    lazy val `cache-manifest`: MediaType = cacheManifest

    lazy val calendar: MediaType =
      MediaType("text", "calendar", compressible = false, binary = false, fileExtensions = List("ics", "ifb"))

    lazy val cmd: MediaType =
      MediaType("text", "cmd", compressible = true, binary = false)

    lazy val coffeescript: MediaType =
      MediaType("text", "coffeescript", compressible = false, binary = false, fileExtensions = List("coffee", "litcoffee"))

    lazy val cql: MediaType =
      MediaType("text", "cql", compressible = false, binary = false)

    lazy val cqlExpression: MediaType =
      MediaType("text", "cql-expression", compressible = false, binary = false)
    
    lazy val `cql-expression`: MediaType = cqlExpression

    lazy val cqlIdentifier: MediaType =
      MediaType("text", "cql-identifier", compressible = false, binary = false)
    
    lazy val `cql-identifier`: MediaType = cqlIdentifier

    lazy val css: MediaType =
      MediaType("text", "css", compressible = true, binary = false, fileExtensions = List("css"))

    lazy val csv: MediaType =
      MediaType("text", "csv", compressible = true, binary = false, fileExtensions = List("csv"))

    lazy val csvSchema: MediaType =
      MediaType("text", "csv-schema", compressible = false, binary = false)
    
    lazy val `csv-schema`: MediaType = csvSchema

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
    
    lazy val `grammar-ref-list`: MediaType = grammarRefList

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
    
    lazy val `jcr-cnd`: MediaType = jcrCnd

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
      MediaType("text", "plain", compressible = true, binary = false, fileExtensions = List("txt", "text", "conf", "def", "list", "log", "in", "ini"))

    lazy val provenanceNotation: MediaType =
      MediaType("text", "provenance-notation", compressible = false, binary = false)
    
    lazy val `provenance-notation`: MediaType = provenanceNotation

    lazy val prsFallensteinRst: MediaType =
      MediaType("text", "prs.fallenstein.rst", compressible = false, binary = false)
    
    lazy val `prs.fallenstein.rst`: MediaType = prsFallensteinRst

    lazy val prsLinesTag: MediaType =
      MediaType("text", "prs.lines.tag", compressible = false, binary = false, fileExtensions = List("dsc"))
    
    lazy val `prs.lines.tag`: MediaType = prsLinesTag

    lazy val prsPropLogic: MediaType =
      MediaType("text", "prs.prop.logic", compressible = false, binary = false)
    
    lazy val `prs.prop.logic`: MediaType = prsPropLogic

    lazy val prsTexi: MediaType =
      MediaType("text", "prs.texi", compressible = false, binary = false)
    
    lazy val `prs.texi`: MediaType = prsTexi

    lazy val raptorfec: MediaType =
      MediaType("text", "raptorfec", compressible = false, binary = false)

    lazy val red: MediaType =
      MediaType("text", "red", compressible = false, binary = false)

    lazy val rfc822Headers: MediaType =
      MediaType("text", "rfc822-headers", compressible = false, binary = false)
    
    lazy val `rfc822-headers`: MediaType = rfc822Headers

    lazy val richtext: MediaType =
      MediaType("text", "richtext", compressible = true, binary = false, fileExtensions = List("rtx"))

    lazy val rtf: MediaType =
      MediaType("text", "rtf", compressible = true, binary = false, fileExtensions = List("rtf"))

    lazy val rtpEncAescm128: MediaType =
      MediaType("text", "rtp-enc-aescm128", compressible = false, binary = false)
    
    lazy val `rtp-enc-aescm128`: MediaType = rtpEncAescm128

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
    
    lazy val `tab-separated-values`: MediaType = tabSeparatedValues

    lazy val troff: MediaType =
      MediaType("text", "troff", compressible = false, binary = false, fileExtensions = List("t", "tr", "roff", "man", "me", "ms"))

    lazy val turtle: MediaType =
      MediaType("text", "turtle", compressible = false, binary = false, fileExtensions = List("ttl"))

    lazy val ulpfec: MediaType =
      MediaType("text", "ulpfec", compressible = false, binary = false)

    lazy val uriList: MediaType =
      MediaType("text", "uri-list", compressible = true, binary = false, fileExtensions = List("uri", "uris", "urls"))
    
    lazy val `uri-list`: MediaType = uriList

    lazy val vcard: MediaType =
      MediaType("text", "vcard", compressible = true, binary = false, fileExtensions = List("vcard"))

    lazy val vndA: MediaType =
      MediaType("text", "vnd.a", compressible = false, binary = false)
    
    lazy val `vnd.a`: MediaType = vndA

    lazy val vndAbc: MediaType =
      MediaType("text", "vnd.abc", compressible = false, binary = false)
    
    lazy val `vnd.abc`: MediaType = vndAbc

    lazy val vndAsciiArt: MediaType =
      MediaType("text", "vnd.ascii-art", compressible = false, binary = false)
    
    lazy val `vnd.ascii-art`: MediaType = vndAsciiArt

    lazy val vndCurl: MediaType =
      MediaType("text", "vnd.curl", compressible = false, binary = false, fileExtensions = List("curl"))
    
    lazy val `vnd.curl`: MediaType = vndCurl

    lazy val vndCurlDcurl: MediaType =
      MediaType("text", "vnd.curl.dcurl", compressible = false, binary = false, fileExtensions = List("dcurl"))
    
    lazy val `vnd.curl.dcurl`: MediaType = vndCurlDcurl

    lazy val vndCurlMcurl: MediaType =
      MediaType("text", "vnd.curl.mcurl", compressible = false, binary = false, fileExtensions = List("mcurl"))
    
    lazy val `vnd.curl.mcurl`: MediaType = vndCurlMcurl

    lazy val vndCurlScurl: MediaType =
      MediaType("text", "vnd.curl.scurl", compressible = false, binary = false, fileExtensions = List("scurl"))
    
    lazy val `vnd.curl.scurl`: MediaType = vndCurlScurl

    lazy val vndDebianCopyright: MediaType =
      MediaType("text", "vnd.debian.copyright", compressible = false, binary = false)
    
    lazy val `vnd.debian.copyright`: MediaType = vndDebianCopyright

    lazy val vndDmclientscript: MediaType =
      MediaType("text", "vnd.dmclientscript", compressible = false, binary = false)
    
    lazy val `vnd.dmclientscript`: MediaType = vndDmclientscript

    lazy val vndDvbSubtitle: MediaType =
      MediaType("text", "vnd.dvb.subtitle", compressible = false, binary = false, fileExtensions = List("sub"))
    
    lazy val `vnd.dvb.subtitle`: MediaType = vndDvbSubtitle

    lazy val vndEsmertecThemeDescriptor: MediaType =
      MediaType("text", "vnd.esmertec.theme-descriptor", compressible = false, binary = false)
    
    lazy val `vnd.esmertec.theme-descriptor`: MediaType = vndEsmertecThemeDescriptor

    lazy val vndExchangeable: MediaType =
      MediaType("text", "vnd.exchangeable", compressible = false, binary = false)
    
    lazy val `vnd.exchangeable`: MediaType = vndExchangeable

    lazy val vndFamilysearchGedcom: MediaType =
      MediaType("text", "vnd.familysearch.gedcom", compressible = false, binary = false, fileExtensions = List("ged"))
    
    lazy val `vnd.familysearch.gedcom`: MediaType = vndFamilysearchGedcom

    lazy val vndFiclabFlt: MediaType =
      MediaType("text", "vnd.ficlab.flt", compressible = false, binary = false)
    
    lazy val `vnd.ficlab.flt`: MediaType = vndFiclabFlt

    lazy val vndFly: MediaType =
      MediaType("text", "vnd.fly", compressible = false, binary = false, fileExtensions = List("fly"))
    
    lazy val `vnd.fly`: MediaType = vndFly

    lazy val vndFmiFlexstor: MediaType =
      MediaType("text", "vnd.fmi.flexstor", compressible = false, binary = false, fileExtensions = List("flx"))
    
    lazy val `vnd.fmi.flexstor`: MediaType = vndFmiFlexstor

    lazy val vndGml: MediaType =
      MediaType("text", "vnd.gml", compressible = false, binary = false)
    
    lazy val `vnd.gml`: MediaType = vndGml

    lazy val vndGraphviz: MediaType =
      MediaType("text", "vnd.graphviz", compressible = false, binary = false, fileExtensions = List("gv"))
    
    lazy val `vnd.graphviz`: MediaType = vndGraphviz

    lazy val vndHans: MediaType =
      MediaType("text", "vnd.hans", compressible = false, binary = false)
    
    lazy val `vnd.hans`: MediaType = vndHans

    lazy val vndHgl: MediaType =
      MediaType("text", "vnd.hgl", compressible = false, binary = false)
    
    lazy val `vnd.hgl`: MediaType = vndHgl

    lazy val vndIn3d3dml: MediaType =
      MediaType("text", "vnd.in3d.3dml", compressible = false, binary = false, fileExtensions = List("3dml"))
    
    lazy val `vnd.in3d.3dml`: MediaType = vndIn3d3dml

    lazy val vndIn3dSpot: MediaType =
      MediaType("text", "vnd.in3d.spot", compressible = false, binary = false, fileExtensions = List("spot"))
    
    lazy val `vnd.in3d.spot`: MediaType = vndIn3dSpot

    lazy val vndIptcNewsml: MediaType =
      MediaType("text", "vnd.iptc.newsml", compressible = false, binary = false)
    
    lazy val `vnd.iptc.newsml`: MediaType = vndIptcNewsml

    lazy val vndIptcNitf: MediaType =
      MediaType("text", "vnd.iptc.nitf", compressible = false, binary = false)
    
    lazy val `vnd.iptc.nitf`: MediaType = vndIptcNitf

    lazy val vndLatexZ: MediaType =
      MediaType("text", "vnd.latex-z", compressible = false, binary = false)
    
    lazy val `vnd.latex-z`: MediaType = vndLatexZ

    lazy val vndMotorolaReflex: MediaType =
      MediaType("text", "vnd.motorola.reflex", compressible = false, binary = false)
    
    lazy val `vnd.motorola.reflex`: MediaType = vndMotorolaReflex

    lazy val vndMsMediapackage: MediaType =
      MediaType("text", "vnd.ms-mediapackage", compressible = false, binary = false)
    
    lazy val `vnd.ms-mediapackage`: MediaType = vndMsMediapackage

    lazy val vndNet2phoneCommcenterCommand: MediaType =
      MediaType("text", "vnd.net2phone.commcenter.command", compressible = false, binary = false)
    
    lazy val `vnd.net2phone.commcenter.command`: MediaType = vndNet2phoneCommcenterCommand

    lazy val vndRadisysMsmlBasicLayout: MediaType =
      MediaType("text", "vnd.radisys.msml-basic-layout", compressible = false, binary = false)
    
    lazy val `vnd.radisys.msml-basic-layout`: MediaType = vndRadisysMsmlBasicLayout

    lazy val vndSenxWarpscript: MediaType =
      MediaType("text", "vnd.senx.warpscript", compressible = false, binary = false)
    
    lazy val `vnd.senx.warpscript`: MediaType = vndSenxWarpscript

    lazy val vndSiUricatalogue: MediaType =
      MediaType("text", "vnd.si.uricatalogue", compressible = false, binary = false)
    
    lazy val `vnd.si.uricatalogue`: MediaType = vndSiUricatalogue

    lazy val vndSosi: MediaType =
      MediaType("text", "vnd.sosi", compressible = false, binary = false)
    
    lazy val `vnd.sosi`: MediaType = vndSosi

    lazy val vndSunJ2meAppDescriptor: MediaType =
      MediaType("text", "vnd.sun.j2me.app-descriptor", compressible = false, binary = false, fileExtensions = List("jad"))
    
    lazy val `vnd.sun.j2me.app-descriptor`: MediaType = vndSunJ2meAppDescriptor

    lazy val vndTrolltechLinguist: MediaType =
      MediaType("text", "vnd.trolltech.linguist", compressible = false, binary = false)
    
    lazy val `vnd.trolltech.linguist`: MediaType = vndTrolltechLinguist

    lazy val vndTypst: MediaType =
      MediaType("text", "vnd.typst", compressible = false, binary = false)
    
    lazy val `vnd.typst`: MediaType = vndTypst

    lazy val vndVcf: MediaType =
      MediaType("text", "vnd.vcf", compressible = false, binary = false)
    
    lazy val `vnd.vcf`: MediaType = vndVcf

    lazy val vndWapSi: MediaType =
      MediaType("text", "vnd.wap.si", compressible = false, binary = false)
    
    lazy val `vnd.wap.si`: MediaType = vndWapSi

    lazy val vndWapSl: MediaType =
      MediaType("text", "vnd.wap.sl", compressible = false, binary = false)
    
    lazy val `vnd.wap.sl`: MediaType = vndWapSl

    lazy val vndWapWml: MediaType =
      MediaType("text", "vnd.wap.wml", compressible = false, binary = false, fileExtensions = List("wml"))
    
    lazy val `vnd.wap.wml`: MediaType = vndWapWml

    lazy val vndWapWmlscript: MediaType =
      MediaType("text", "vnd.wap.wmlscript", compressible = false, binary = false, fileExtensions = List("wmls"))
    
    lazy val `vnd.wap.wmlscript`: MediaType = vndWapWmlscript

    lazy val vndZooKcl: MediaType =
      MediaType("text", "vnd.zoo.kcl", compressible = false, binary = false)
    
    lazy val `vnd.zoo.kcl`: MediaType = vndZooKcl

    lazy val vtt: MediaType =
      MediaType("text", "vtt", compressible = true, binary = false, fileExtensions = List("vtt"))

    lazy val wgsl: MediaType =
      MediaType("text", "wgsl", compressible = false, binary = false, fileExtensions = List("wgsl"))

    lazy val xAsm: MediaType =
      MediaType("text", "x-asm", compressible = false, binary = false, fileExtensions = List("s", "asm"))
    
    lazy val `x-asm`: MediaType = xAsm

    lazy val xC: MediaType =
      MediaType("text", "x-c", compressible = false, binary = false, fileExtensions = List("c", "cc", "cxx", "cpp", "h", "hh", "dic"))
    
    lazy val `x-c`: MediaType = xC

    lazy val xComponent: MediaType =
      MediaType("text", "x-component", compressible = true, binary = false, fileExtensions = List("htc"))
    
    lazy val `x-component`: MediaType = xComponent

    lazy val xFortran: MediaType =
      MediaType("text", "x-fortran", compressible = false, binary = false, fileExtensions = List("f", "for", "f77", "f90"))
    
    lazy val `x-fortran`: MediaType = xFortran

    lazy val xGwtRpc: MediaType =
      MediaType("text", "x-gwt-rpc", compressible = true, binary = false)
    
    lazy val `x-gwt-rpc`: MediaType = xGwtRpc

    lazy val xHandlebarsTemplate: MediaType =
      MediaType("text", "x-handlebars-template", compressible = false, binary = false, fileExtensions = List("hbs"))
    
    lazy val `x-handlebars-template`: MediaType = xHandlebarsTemplate

    lazy val xJavaSource: MediaType =
      MediaType("text", "x-java-source", compressible = false, binary = false, fileExtensions = List("java"))
    
    lazy val `x-java-source`: MediaType = xJavaSource

    lazy val xJqueryTmpl: MediaType =
      MediaType("text", "x-jquery-tmpl", compressible = true, binary = false)
    
    lazy val `x-jquery-tmpl`: MediaType = xJqueryTmpl

    lazy val xLua: MediaType =
      MediaType("text", "x-lua", compressible = false, binary = false, fileExtensions = List("lua"))
    
    lazy val `x-lua`: MediaType = xLua

    lazy val xMarkdown: MediaType =
      MediaType("text", "x-markdown", compressible = true, binary = false, fileExtensions = List("mkd"))
    
    lazy val `x-markdown`: MediaType = xMarkdown

    lazy val xNfo: MediaType =
      MediaType("text", "x-nfo", compressible = false, binary = false, fileExtensions = List("nfo"))
    
    lazy val `x-nfo`: MediaType = xNfo

    lazy val xOpml: MediaType =
      MediaType("text", "x-opml", compressible = false, binary = false, fileExtensions = List("opml"))
    
    lazy val `x-opml`: MediaType = xOpml

    lazy val xOrg: MediaType =
      MediaType("text", "x-org", compressible = true, binary = false, fileExtensions = List("org"))
    
    lazy val `x-org`: MediaType = xOrg

    lazy val xPascal: MediaType =
      MediaType("text", "x-pascal", compressible = false, binary = false, fileExtensions = List("p", "pas"))
    
    lazy val `x-pascal`: MediaType = xPascal

    lazy val xPhp: MediaType =
      MediaType("text", "x-php", compressible = true, binary = false, fileExtensions = List("php"))
    
    lazy val `x-php`: MediaType = xPhp

    lazy val xProcessing: MediaType =
      MediaType("text", "x-processing", compressible = true, binary = false, fileExtensions = List("pde"))
    
    lazy val `x-processing`: MediaType = xProcessing

    lazy val xSass: MediaType =
      MediaType("text", "x-sass", compressible = false, binary = false, fileExtensions = List("sass"))
    
    lazy val `x-sass`: MediaType = xSass

    lazy val xScss: MediaType =
      MediaType("text", "x-scss", compressible = false, binary = false, fileExtensions = List("scss"))
    
    lazy val `x-scss`: MediaType = xScss

    lazy val xSetext: MediaType =
      MediaType("text", "x-setext", compressible = false, binary = false, fileExtensions = List("etx"))
    
    lazy val `x-setext`: MediaType = xSetext

    lazy val xSfv: MediaType =
      MediaType("text", "x-sfv", compressible = false, binary = false, fileExtensions = List("sfv"))
    
    lazy val `x-sfv`: MediaType = xSfv

    lazy val xSuseYmp: MediaType =
      MediaType("text", "x-suse-ymp", compressible = true, binary = false, fileExtensions = List("ymp"))
    
    lazy val `x-suse-ymp`: MediaType = xSuseYmp

    lazy val xUuencode: MediaType =
      MediaType("text", "x-uuencode", compressible = false, binary = false, fileExtensions = List("uu"))
    
    lazy val `x-uuencode`: MediaType = xUuencode

    lazy val xVcalendar: MediaType =
      MediaType("text", "x-vcalendar", compressible = false, binary = false, fileExtensions = List("vcs"))
    
    lazy val `x-vcalendar`: MediaType = xVcalendar

    lazy val xVcard: MediaType =
      MediaType("text", "x-vcard", compressible = false, binary = false, fileExtensions = List("vcf"))
    
    lazy val `x-vcard`: MediaType = xVcard

    lazy val xml: MediaType =
      MediaType("text", "xml", compressible = true, binary = false, fileExtensions = List("xml"))

    lazy val xmlExternalParsedEntity: MediaType =
      MediaType("text", "xml-external-parsed-entity", compressible = false, binary = false)
    
    lazy val `xml-external-parsed-entity`: MediaType = xmlExternalParsedEntity

    lazy val yaml: MediaType =
      MediaType("text", "yaml", compressible = true, binary = false, fileExtensions = List("yaml", "yml"))

    lazy val any: MediaType = MediaType("text", "*")

    lazy val all: List[MediaType] = List(
      `1d-interleaved-parityfec`,
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
      prsFallensteinRst,
      prsLinesTag,
      prsPropLogic,
      prsTexi,
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
      vndA,
      vndAbc,
      vndAsciiArt,
      vndCurl,
      vndCurlDcurl,
      vndCurlMcurl,
      vndCurlScurl,
      vndDebianCopyright,
      vndDmclientscript,
      vndDvbSubtitle,
      vndEsmertecThemeDescriptor,
      vndExchangeable,
      vndFamilysearchGedcom,
      vndFiclabFlt,
      vndFly,
      vndFmiFlexstor,
      vndGml,
      vndGraphviz,
      vndHans,
      vndHgl,
      vndIn3d3dml,
      vndIn3dSpot,
      vndIptcNewsml,
      vndIptcNitf,
      vndLatexZ,
      vndMotorolaReflex,
      vndMsMediapackage,
      vndNet2phoneCommcenterCommand,
      vndRadisysMsmlBasicLayout,
      vndSenxWarpscript,
      vndSiUricatalogue,
      vndSosi,
      vndSunJ2meAppDescriptor,
      vndTrolltechLinguist,
      vndTypst,
      vndVcf,
      vndWapSi,
      vndWapSl,
      vndWapWml,
      vndWapWmlscript,
      vndZooKcl,
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
    lazy val `1d-interleaved-parityfec`: MediaType =
      MediaType("video", "1d-interleaved-parityfec", compressible = false, binary = true)

    lazy val `3gpp`: MediaType =
      MediaType("video", "3gpp", compressible = false, binary = true, fileExtensions = List("3gp", "3gpp"))

    lazy val `3gpp-tt`: MediaType =
      MediaType("video", "3gpp-tt", compressible = false, binary = true)

    lazy val `3gpp2`: MediaType =
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
    
    lazy val `h263-1998`: MediaType = h2631998

    lazy val h2632000: MediaType =
      MediaType("video", "h263-2000", compressible = false, binary = true)
    
    lazy val `h263-2000`: MediaType = h2632000

    lazy val h264: MediaType =
      MediaType("video", "h264", compressible = false, binary = true, fileExtensions = List("h264"))

    lazy val h264Rcdo: MediaType =
      MediaType("video", "h264-rcdo", compressible = false, binary = true)
    
    lazy val `h264-rcdo`: MediaType = h264Rcdo

    lazy val h264Svc: MediaType =
      MediaType("video", "h264-svc", compressible = false, binary = true)
    
    lazy val `h264-svc`: MediaType = h264Svc

    lazy val h265: MediaType =
      MediaType("video", "h265", compressible = false, binary = true)

    lazy val h266: MediaType =
      MediaType("video", "h266", compressible = false, binary = true)

    lazy val isoSegment: MediaType =
      MediaType("video", "iso.segment", compressible = false, binary = true, fileExtensions = List("m4s"))
    
    lazy val `iso.segment`: MediaType = isoSegment

    lazy val jpeg: MediaType =
      MediaType("video", "jpeg", compressible = false, binary = true, fileExtensions = List("jpgv"))

    lazy val jpeg2000: MediaType =
      MediaType("video", "jpeg2000", compressible = false, binary = true)

    lazy val jpeg2000Scl: MediaType =
      MediaType("video", "jpeg2000-scl", compressible = false, binary = true)
    
    lazy val `jpeg2000-scl`: MediaType = jpeg2000Scl

    lazy val jpm: MediaType =
      MediaType("video", "jpm", compressible = false, binary = true, fileExtensions = List("jpm", "jpgm"))

    lazy val jxsv: MediaType =
      MediaType("video", "jxsv", compressible = false, binary = true)

    lazy val lottieJson: MediaType =
      MediaType("video", "lottie+json", compressible = true, binary = true)
    
    lazy val `lottie+json`: MediaType = lottieJson

    lazy val matroska: MediaType =
      MediaType("video", "matroska", compressible = false, binary = true, fileExtensions = List("mkv"))

    lazy val matroska3d: MediaType =
      MediaType("video", "matroska-3d", compressible = false, binary = true, fileExtensions = List("mk3d"))
    
    lazy val `matroska-3d`: MediaType = matroska3d

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
    
    lazy val `mp4v-es`: MediaType = mp4vEs

    lazy val mpeg: MediaType =
      MediaType("video", "mpeg", compressible = false, binary = true, fileExtensions = List("mpeg", "mpg", "mpe", "m1v", "m2v"))

    lazy val mpeg4Generic: MediaType =
      MediaType("video", "mpeg4-generic", compressible = false, binary = true)
    
    lazy val `mpeg4-generic`: MediaType = mpeg4Generic

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
    
    lazy val `rtp-enc-aescm128`: MediaType = rtpEncAescm128

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

    lazy val vndBlockfactFactv: MediaType =
      MediaType("video", "vnd.blockfact.factv", compressible = false, binary = true)
    
    lazy val `vnd.blockfact.factv`: MediaType = vndBlockfactFactv

    lazy val vndCctv: MediaType =
      MediaType("video", "vnd.cctv", compressible = false, binary = true)
    
    lazy val `vnd.cctv`: MediaType = vndCctv

    lazy val vndDeceHd: MediaType =
      MediaType("video", "vnd.dece.hd", compressible = false, binary = true, fileExtensions = List("uvh", "uvvh"))
    
    lazy val `vnd.dece.hd`: MediaType = vndDeceHd

    lazy val vndDeceMobile: MediaType =
      MediaType("video", "vnd.dece.mobile", compressible = false, binary = true, fileExtensions = List("uvm", "uvvm"))
    
    lazy val `vnd.dece.mobile`: MediaType = vndDeceMobile

    lazy val vndDeceMp4: MediaType =
      MediaType("video", "vnd.dece.mp4", compressible = false, binary = true)
    
    lazy val `vnd.dece.mp4`: MediaType = vndDeceMp4

    lazy val vndDecePd: MediaType =
      MediaType("video", "vnd.dece.pd", compressible = false, binary = true, fileExtensions = List("uvp", "uvvp"))
    
    lazy val `vnd.dece.pd`: MediaType = vndDecePd

    lazy val vndDeceSd: MediaType =
      MediaType("video", "vnd.dece.sd", compressible = false, binary = true, fileExtensions = List("uvs", "uvvs"))
    
    lazy val `vnd.dece.sd`: MediaType = vndDeceSd

    lazy val vndDeceVideo: MediaType =
      MediaType("video", "vnd.dece.video", compressible = false, binary = true, fileExtensions = List("uvv", "uvvv"))
    
    lazy val `vnd.dece.video`: MediaType = vndDeceVideo

    lazy val vndDirectvMpeg: MediaType =
      MediaType("video", "vnd.directv.mpeg", compressible = false, binary = true)
    
    lazy val `vnd.directv.mpeg`: MediaType = vndDirectvMpeg

    lazy val vndDirectvMpegTts: MediaType =
      MediaType("video", "vnd.directv.mpeg-tts", compressible = false, binary = true)
    
    lazy val `vnd.directv.mpeg-tts`: MediaType = vndDirectvMpegTts

    lazy val vndDlnaMpegTts: MediaType =
      MediaType("video", "vnd.dlna.mpeg-tts", compressible = false, binary = true)
    
    lazy val `vnd.dlna.mpeg-tts`: MediaType = vndDlnaMpegTts

    lazy val vndDvbFile: MediaType =
      MediaType("video", "vnd.dvb.file", compressible = false, binary = true, fileExtensions = List("dvb"))
    
    lazy val `vnd.dvb.file`: MediaType = vndDvbFile

    lazy val vndFvt: MediaType =
      MediaType("video", "vnd.fvt", compressible = false, binary = true, fileExtensions = List("fvt"))
    
    lazy val `vnd.fvt`: MediaType = vndFvt

    lazy val vndHnsVideo: MediaType =
      MediaType("video", "vnd.hns.video", compressible = false, binary = true)
    
    lazy val `vnd.hns.video`: MediaType = vndHnsVideo

    lazy val vndIptvforum1dparityfec1010: MediaType =
      MediaType("video", "vnd.iptvforum.1dparityfec-1010", compressible = false, binary = true)
    
    lazy val `vnd.iptvforum.1dparityfec-1010`: MediaType = vndIptvforum1dparityfec1010

    lazy val vndIptvforum1dparityfec2005: MediaType =
      MediaType("video", "vnd.iptvforum.1dparityfec-2005", compressible = false, binary = true)
    
    lazy val `vnd.iptvforum.1dparityfec-2005`: MediaType = vndIptvforum1dparityfec2005

    lazy val vndIptvforum2dparityfec1010: MediaType =
      MediaType("video", "vnd.iptvforum.2dparityfec-1010", compressible = false, binary = true)
    
    lazy val `vnd.iptvforum.2dparityfec-1010`: MediaType = vndIptvforum2dparityfec1010

    lazy val vndIptvforum2dparityfec2005: MediaType =
      MediaType("video", "vnd.iptvforum.2dparityfec-2005", compressible = false, binary = true)
    
    lazy val `vnd.iptvforum.2dparityfec-2005`: MediaType = vndIptvforum2dparityfec2005

    lazy val vndIptvforumTtsavc: MediaType =
      MediaType("video", "vnd.iptvforum.ttsavc", compressible = false, binary = true)
    
    lazy val `vnd.iptvforum.ttsavc`: MediaType = vndIptvforumTtsavc

    lazy val vndIptvforumTtsmpeg2: MediaType =
      MediaType("video", "vnd.iptvforum.ttsmpeg2", compressible = false, binary = true)
    
    lazy val `vnd.iptvforum.ttsmpeg2`: MediaType = vndIptvforumTtsmpeg2

    lazy val vndMotorolaVideo: MediaType =
      MediaType("video", "vnd.motorola.video", compressible = false, binary = true)
    
    lazy val `vnd.motorola.video`: MediaType = vndMotorolaVideo

    lazy val vndMotorolaVideop: MediaType =
      MediaType("video", "vnd.motorola.videop", compressible = false, binary = true)
    
    lazy val `vnd.motorola.videop`: MediaType = vndMotorolaVideop

    lazy val vndMpegurl: MediaType =
      MediaType("video", "vnd.mpegurl", compressible = false, binary = true, fileExtensions = List("mxu", "m4u"))
    
    lazy val `vnd.mpegurl`: MediaType = vndMpegurl

    lazy val vndMsPlayreadyMediaPyv: MediaType =
      MediaType("video", "vnd.ms-playready.media.pyv", compressible = false, binary = true, fileExtensions = List("pyv"))
    
    lazy val `vnd.ms-playready.media.pyv`: MediaType = vndMsPlayreadyMediaPyv

    lazy val vndNokiaInterleavedMultimedia: MediaType =
      MediaType("video", "vnd.nokia.interleaved-multimedia", compressible = false, binary = true)
    
    lazy val `vnd.nokia.interleaved-multimedia`: MediaType = vndNokiaInterleavedMultimedia

    lazy val vndNokiaMp4vr: MediaType =
      MediaType("video", "vnd.nokia.mp4vr", compressible = false, binary = true)
    
    lazy val `vnd.nokia.mp4vr`: MediaType = vndNokiaMp4vr

    lazy val vndNokiaVideovoip: MediaType =
      MediaType("video", "vnd.nokia.videovoip", compressible = false, binary = true)
    
    lazy val `vnd.nokia.videovoip`: MediaType = vndNokiaVideovoip

    lazy val vndObjectvideo: MediaType =
      MediaType("video", "vnd.objectvideo", compressible = false, binary = true)
    
    lazy val `vnd.objectvideo`: MediaType = vndObjectvideo

    lazy val vndPlanar: MediaType =
      MediaType("video", "vnd.planar", compressible = false, binary = true)
    
    lazy val `vnd.planar`: MediaType = vndPlanar

    lazy val vndRadgamettoolsBink: MediaType =
      MediaType("video", "vnd.radgamettools.bink", compressible = false, binary = true)
    
    lazy val `vnd.radgamettools.bink`: MediaType = vndRadgamettoolsBink

    lazy val vndRadgamettoolsSmacker: MediaType =
      MediaType("video", "vnd.radgamettools.smacker", compressible = false, binary = true)
    
    lazy val `vnd.radgamettools.smacker`: MediaType = vndRadgamettoolsSmacker

    lazy val vndSealedMpeg1: MediaType =
      MediaType("video", "vnd.sealed.mpeg1", compressible = false, binary = true)
    
    lazy val `vnd.sealed.mpeg1`: MediaType = vndSealedMpeg1

    lazy val vndSealedMpeg4: MediaType =
      MediaType("video", "vnd.sealed.mpeg4", compressible = false, binary = true)
    
    lazy val `vnd.sealed.mpeg4`: MediaType = vndSealedMpeg4

    lazy val vndSealedSwf: MediaType =
      MediaType("video", "vnd.sealed.swf", compressible = false, binary = true)
    
    lazy val `vnd.sealed.swf`: MediaType = vndSealedSwf

    lazy val vndSealedmediaSoftsealMov: MediaType =
      MediaType("video", "vnd.sealedmedia.softseal.mov", compressible = false, binary = true)
    
    lazy val `vnd.sealedmedia.softseal.mov`: MediaType = vndSealedmediaSoftsealMov

    lazy val vndUvvuMp4: MediaType =
      MediaType("video", "vnd.uvvu.mp4", compressible = false, binary = true, fileExtensions = List("uvu", "uvvu"))
    
    lazy val `vnd.uvvu.mp4`: MediaType = vndUvvuMp4

    lazy val vndVivo: MediaType =
      MediaType("video", "vnd.vivo", compressible = false, binary = true, fileExtensions = List("viv"))
    
    lazy val `vnd.vivo`: MediaType = vndVivo

    lazy val vndYoutubeYt: MediaType =
      MediaType("video", "vnd.youtube.yt", compressible = false, binary = true)
    
    lazy val `vnd.youtube.yt`: MediaType = vndYoutubeYt

    lazy val vp8: MediaType =
      MediaType("video", "vp8", compressible = false, binary = true)

    lazy val vp9: MediaType =
      MediaType("video", "vp9", compressible = false, binary = true)

    lazy val webm: MediaType =
      MediaType("video", "webm", compressible = false, binary = true, fileExtensions = List("webm"))

    lazy val xF4v: MediaType =
      MediaType("video", "x-f4v", compressible = false, binary = true, fileExtensions = List("f4v"))
    
    lazy val `x-f4v`: MediaType = xF4v

    lazy val xFli: MediaType =
      MediaType("video", "x-fli", compressible = false, binary = true, fileExtensions = List("fli"))
    
    lazy val `x-fli`: MediaType = xFli

    lazy val xFlv: MediaType =
      MediaType("video", "x-flv", compressible = false, binary = true, fileExtensions = List("flv"))
    
    lazy val `x-flv`: MediaType = xFlv

    lazy val xM4v: MediaType =
      MediaType("video", "x-m4v", compressible = false, binary = true, fileExtensions = List("m4v"))
    
    lazy val `x-m4v`: MediaType = xM4v

    lazy val xMatroska: MediaType =
      MediaType("video", "x-matroska", compressible = false, binary = true, fileExtensions = List("mkv", "mk3d", "mks"))
    
    lazy val `x-matroska`: MediaType = xMatroska

    lazy val xMng: MediaType =
      MediaType("video", "x-mng", compressible = false, binary = true, fileExtensions = List("mng"))
    
    lazy val `x-mng`: MediaType = xMng

    lazy val xMsAsf: MediaType =
      MediaType("video", "x-ms-asf", compressible = false, binary = true, fileExtensions = List("asf", "asx"))
    
    lazy val `x-ms-asf`: MediaType = xMsAsf

    lazy val xMsVob: MediaType =
      MediaType("video", "x-ms-vob", compressible = false, binary = true, fileExtensions = List("vob"))
    
    lazy val `x-ms-vob`: MediaType = xMsVob

    lazy val xMsWm: MediaType =
      MediaType("video", "x-ms-wm", compressible = false, binary = true, fileExtensions = List("wm"))
    
    lazy val `x-ms-wm`: MediaType = xMsWm

    lazy val xMsWmv: MediaType =
      MediaType("video", "x-ms-wmv", compressible = false, binary = true, fileExtensions = List("wmv"))
    
    lazy val `x-ms-wmv`: MediaType = xMsWmv

    lazy val xMsWmx: MediaType =
      MediaType("video", "x-ms-wmx", compressible = false, binary = true, fileExtensions = List("wmx"))
    
    lazy val `x-ms-wmx`: MediaType = xMsWmx

    lazy val xMsWvx: MediaType =
      MediaType("video", "x-ms-wvx", compressible = false, binary = true, fileExtensions = List("wvx"))
    
    lazy val `x-ms-wvx`: MediaType = xMsWvx

    lazy val xMsvideo: MediaType =
      MediaType("video", "x-msvideo", compressible = false, binary = true, fileExtensions = List("avi"))
    
    lazy val `x-msvideo`: MediaType = xMsvideo

    lazy val xSgiMovie: MediaType =
      MediaType("video", "x-sgi-movie", compressible = false, binary = true, fileExtensions = List("movie"))
    
    lazy val `x-sgi-movie`: MediaType = xSgiMovie

    lazy val xSmv: MediaType =
      MediaType("video", "x-smv", compressible = false, binary = true, fileExtensions = List("smv"))
    
    lazy val `x-smv`: MediaType = xSmv

    lazy val any: MediaType = MediaType("video", "*")

    lazy val all: List[MediaType] = List(
      `1d-interleaved-parityfec`,
      `3gpp`,
      `3gpp-tt`,
      `3gpp2`,
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
      isoSegment,
      jpeg,
      jpeg2000,
      jpeg2000Scl,
      jpm,
      jxsv,
      lottieJson,
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
      vndBlockfactFactv,
      vndCctv,
      vndDeceHd,
      vndDeceMobile,
      vndDeceMp4,
      vndDecePd,
      vndDeceSd,
      vndDeceVideo,
      vndDirectvMpeg,
      vndDirectvMpegTts,
      vndDlnaMpegTts,
      vndDvbFile,
      vndFvt,
      vndHnsVideo,
      vndIptvforum1dparityfec1010,
      vndIptvforum1dparityfec2005,
      vndIptvforum2dparityfec1010,
      vndIptvforum2dparityfec2005,
      vndIptvforumTtsavc,
      vndIptvforumTtsmpeg2,
      vndMotorolaVideo,
      vndMotorolaVideop,
      vndMpegurl,
      vndMsPlayreadyMediaPyv,
      vndNokiaInterleavedMultimedia,
      vndNokiaMp4vr,
      vndNokiaVideovoip,
      vndObjectvideo,
      vndPlanar,
      vndRadgamettoolsBink,
      vndRadgamettoolsSmacker,
      vndSealedMpeg1,
      vndSealedMpeg4,
      vndSealedSwf,
      vndSealedmediaSoftsealMov,
      vndUvvuMp4,
      vndVivo,
      vndYoutubeYt,
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
    
    lazy val `x-cooltalk`: MediaType = xCooltalk

    lazy val any: MediaType = MediaType("x-conference", "*")

    lazy val all: List[MediaType] = List(
      xCooltalk
    )
  }

  object x_shader {
    lazy val xFragment: MediaType =
      MediaType("x-shader", "x-fragment", compressible = true, binary = false)
    
    lazy val `x-fragment`: MediaType = xFragment

    lazy val xVertex: MediaType =
      MediaType("x-shader", "x-vertex", compressible = true, binary = false)
    
    lazy val `x-vertex`: MediaType = xVertex

    lazy val any: MediaType = MediaType("x-shader", "*")

    lazy val all: List[MediaType] = List(
      xFragment,
      xVertex
    )
  }

  lazy val allMediaTypes: List[MediaType] = application.all ++ audio.all ++ chemical.all ++ font.all ++ image.all ++ message.all ++ model.all ++ multipart.all ++ text.all ++ video.all ++ x_conference.all ++ x_shader.all
}
