package golem.ai

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

/**
 * Scala.js wrapper for `golem:video-generation/...@1.0.0`.
 *
 * Public API avoids `scala.scalajs.js.*` types.
 */
object VideoGeneration {
  // ----- Model types -----------------------------------------------------------------------

  sealed trait VideoError extends Product with Serializable { def tag: String }
  object VideoError {
    final case class InvalidInput(message: String)       extends VideoError { val tag: String = "invalid-input"       }
    final case class UnsupportedFeature(message: String) extends VideoError { val tag: String = "unsupported-feature" }
    case object QuotaExceeded                            extends VideoError { val tag: String = "quota-exceeded"      }
    final case class GenerationFailed(message: String)   extends VideoError { val tag: String = "generation-failed"   }
    case object Cancelled                                extends VideoError { val tag: String = "cancelled"           }
    final case class InternalError(message: String)      extends VideoError { val tag: String = "internal-error"      }
  }

  sealed trait MediaInput extends Product with Serializable
  object MediaInput {
    final case class Text(value: String)     extends MediaInput
    final case class Image(value: Reference) extends MediaInput
    final case class Video(value: BaseVideo) extends MediaInput
  }

  final case class Reference(data: InputImage, prompt: Option[String], role: Option[ImageRole])

  sealed trait ImageRole extends Product with Serializable { def tag: String }
  object ImageRole {
    case object First extends ImageRole { val tag: String = "first" }
    case object Last  extends ImageRole { val tag: String = "last"  }

    def fromTag(tag: String): ImageRole = if (tag == "last") Last else First
  }

  final case class InputImage(data: MediaData)
  final case class BaseVideo(data: MediaData)
  final case class Narration(data: MediaData)

  sealed trait MediaData extends Product with Serializable
  object MediaData {
    final case class Url(value: String)     extends MediaData
    final case class Bytes(value: RawBytes) extends MediaData
  }

  final case class RawBytes(bytes: Array[Byte], mimeType: String)

  final case class StaticMask(mask: InputImage)
  final case class DynamicMask(mask: InputImage, trajectories: List[Position])
  final case class Position(x: Int, y: Int)

  sealed trait CameraMovement extends Product with Serializable
  object CameraMovement {
    final case class Simple(config: CameraConfig) extends CameraMovement
    case object DownBack                          extends CameraMovement
    case object ForwardUp                         extends CameraMovement
    case object RightTurnForward                  extends CameraMovement
    case object LeftTurnForward                   extends CameraMovement
  }

  final case class CameraConfig(
    horizontal: Float,
    vertical: Float,
    pan: Float,
    tilt: Float,
    zoom: Float,
    roll: Float
  )

  final case class GenerationConfig(
    negativePrompt: Option[String] = None,
    seed: Option[BigInt] = None,
    scheduler: Option[String] = None,
    guidanceScale: Option[Float] = None,
    aspectRatio: Option[AspectRatio] = None,
    durationSeconds: Option[Float] = None,
    resolution: Option[Resolution] = None,
    model: Option[String] = None,
    enableAudio: Option[Boolean] = None,
    enhancePrompt: Option[Boolean] = None,
    providerOptions: Option[List[Kv]] = None,
    lastframe: Option[InputImage] = None,
    staticMask: Option[StaticMask] = None,
    dynamicMask: Option[DynamicMask] = None,
    cameraControl: Option[CameraMovement] = None
  )

  sealed trait AspectRatio extends Product with Serializable { def tag: String }
  object AspectRatio {
    case object Square    extends AspectRatio { val tag: String = "square"    }
    case object Portrait  extends AspectRatio { val tag: String = "portrait"  }
    case object Landscape extends AspectRatio { val tag: String = "landscape" }
    case object Cinema    extends AspectRatio { val tag: String = "cinema"    }

    def fromTag(tag: String): AspectRatio =
      tag match {
        case "portrait"  => Portrait
        case "landscape" => Landscape
        case "cinema"    => Cinema
        case _           => Square
      }
  }

  sealed trait Resolution extends Product with Serializable { def tag: String }
  object Resolution {
    case object Sd  extends Resolution { val tag: String = "sd"  }
    case object Hd  extends Resolution { val tag: String = "hd"  }
    case object Fhd extends Resolution { val tag: String = "fhd" }
    case object Uhd extends Resolution { val tag: String = "uhd" }

    def fromTag(tag: String): Resolution =
      tag match {
        case "hd"  => Hd
        case "fhd" => Fhd
        case "uhd" => Uhd
        case _     => Sd
      }
  }

  final case class Kv(key: String, value: String)

  final case class Video(
    uri: Option[String],
    base64Bytes: Option[Array[Byte]],
    mimeType: String,
    width: Option[Int],
    height: Option[Int],
    fps: Option[Float],
    durationSeconds: Option[Float],
    generationId: Option[String]
  )

  sealed trait JobStatus extends Product with Serializable { def tag: String }
  object JobStatus {
    case object Pending                      extends JobStatus { val tag: String = "pending"   }
    case object Running                      extends JobStatus { val tag: String = "running"   }
    case object Succeeded                    extends JobStatus { val tag: String = "succeeded" }
    final case class Failed(message: String) extends JobStatus { val tag: String = "failed"    }
  }

  final case class VideoResult(status: JobStatus, videos: Option[List[Video]])

  final case class TextToSpeech(text: String, voiceId: String, language: VoiceLanguage, speed: Float)

  sealed trait AudioSource extends Product with Serializable
  object AudioSource {
    final case class FromText(value: TextToSpeech) extends AudioSource
    final case class FromAudio(value: Narration)   extends AudioSource
  }

  final case class VoiceInfo(voiceId: String, name: String, language: VoiceLanguage, previewUrl: Option[String])

  sealed trait VoiceLanguage extends Product with Serializable { def tag: String }
  object VoiceLanguage {
    case object En extends VoiceLanguage { val tag: String = "en" }
    case object Zh extends VoiceLanguage { val tag: String = "zh" }

    def fromTag(tag: String): VoiceLanguage = if (tag == "zh") Zh else En
  }

  sealed trait SingleImageEffects extends Product with Serializable { def tag: String }
  object SingleImageEffects {
    case object Bloombloom   extends SingleImageEffects { val tag: String = "bloombloom"   }
    case object Dizzydizzy   extends SingleImageEffects { val tag: String = "dizzydizzy"   }
    case object Fuzzyfuzzy   extends SingleImageEffects { val tag: String = "fuzzyfuzzy"   }
    case object Squish       extends SingleImageEffects { val tag: String = "squish"       }
    case object Expansion    extends SingleImageEffects { val tag: String = "expansion"    }
    case object AnimeFigure  extends SingleImageEffects { val tag: String = "anime-figure" }
    case object Rocketrocket extends SingleImageEffects { val tag: String = "rocketrocket" }

    def fromTag(tag: String): SingleImageEffects =
      tag match {
        case "dizzydizzy"   => Dizzydizzy
        case "fuzzyfuzzy"   => Fuzzyfuzzy
        case "squish"       => Squish
        case "expansion"    => Expansion
        case "anime-figure" => AnimeFigure
        case "rocketrocket" => Rocketrocket
        case _              => Bloombloom
      }
  }

  sealed trait DualImageEffects extends Product with Serializable { def tag: String }
  object DualImageEffects {
    case object Hug          extends DualImageEffects { val tag: String = "hug"           }
    case object Kiss         extends DualImageEffects { val tag: String = "kiss"          }
    case object HeartGesture extends DualImageEffects { val tag: String = "heart-gesture" }

    def fromTag(tag: String): DualImageEffects =
      tag match {
        case "kiss"          => Kiss
        case "heart-gesture" => HeartGesture
        case _               => Hug
      }
  }

  final case class DualEffect(effect: DualImageEffects, secondImage: InputImage)

  sealed trait EffectType extends Product with Serializable
  object EffectType {
    final case class Single(value: SingleImageEffects) extends EffectType
    final case class Dual(value: DualEffect)           extends EffectType
  }

  sealed trait LipSyncVideo extends Product with Serializable
  object LipSyncVideo {
    final case class Video(value: BaseVideo) extends LipSyncVideo
    final case class VideoId(value: String)  extends LipSyncVideo
  }

  // ----- Advanced options -----------------------------------------------------------------

  final case class ExtendVideoOptions(
    videoId: String,
    prompt: Option[String],
    negativePrompt: Option[String],
    cfgScale: Option[Float],
    providerOptions: Option[List[Kv]]
  )

  final case class GenerateVideoEffectsOptions(
    input: InputImage,
    effect: EffectType,
    model: Option[String],
    duration: Option[Float],
    mode: Option[String]
  )

  final case class MultiImageGenerationOptions(
    inputImages: List[InputImage],
    prompt: Option[String],
    config: GenerationConfig
  )

  // ----- Public API -----------------------------------------------------------------------

  def generateResult(input: MediaInput, config: GenerationConfig): Either[VideoError, String] =
    decodeResult(VideoModule.generate(toJsMediaInput(input), toJsGenerationConfig(config)))(
      _.toString,
      fromJsVideoError
    )

  def generate(input: MediaInput, config: GenerationConfig): String =
    generateResult(input, config) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(s"Video error ${err.tag}")
    }

  def pollResult(jobId: String): Either[VideoError, VideoResult] =
    decodeResult(VideoModule.poll(jobId))(fromJsVideoResult, fromJsVideoError)

  def poll(jobId: String): VideoResult =
    pollResult(jobId) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(s"Video error ${err.tag}")
    }

  def cancelResult(jobId: String): Either[VideoError, String] =
    decodeResult(VideoModule.cancel(jobId))(_.toString, fromJsVideoError)

  def cancel(jobId: String): String =
    cancelResult(jobId) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(s"Video error ${err.tag}")
    }

  object LipSync {
    def generateLipSyncResult(video: LipSyncVideo, audio: AudioSource): Either[VideoError, String] =
      decodeResult(LipSyncModule.generateLipSync(toJsLipSyncVideo(video), toJsAudioSource(audio)))(
        _.toString,
        fromJsVideoError
      )

    def generateLipSync(video: LipSyncVideo, audio: AudioSource): String =
      generateLipSyncResult(video, audio) match {
        case Right(value) => value
        case Left(err)    => throw new IllegalStateException(s"Video error ${err.tag}")
      }

    def listVoicesResult(language: Option[String]): Either[VideoError, List[VoiceInfo]] =
      decodeResult(LipSyncModule.listVoices(language.fold[js.Any](js.undefined)(identity)))(
        fromJsVoiceList,
        fromJsVideoError
      )

    def listVoices(language: Option[String]): List[VoiceInfo] =
      listVoicesResult(language) match {
        case Right(value) => value
        case Left(err)    => throw new IllegalStateException(s"Video error ${err.tag}")
      }
  }

  object Advanced {
    def extendVideoResult(options: ExtendVideoOptions): Either[VideoError, String] =
      decodeResult(AdvancedModule.extendVideo(toJsExtendVideoOptions(options)))(_.toString, fromJsVideoError)

    def extendVideo(options: ExtendVideoOptions): String =
      extendVideoResult(options) match {
        case Right(value) => value
        case Left(err)    => throw new IllegalStateException(s"Video error ${err.tag}")
      }

    def upscaleVideoResult(input: BaseVideo): Either[VideoError, String] =
      decodeResult(AdvancedModule.upscaleVideo(toJsBaseVideo(input)))(_.toString, fromJsVideoError)

    def upscaleVideo(input: BaseVideo): String =
      upscaleVideoResult(input) match {
        case Right(value) => value
        case Left(err)    => throw new IllegalStateException(s"Video error ${err.tag}")
      }

    def generateVideoEffectsResult(options: GenerateVideoEffectsOptions): Either[VideoError, String] =
      decodeResult(AdvancedModule.generateVideoEffects(toJsGenerateVideoEffectsOptions(options)))(
        _.toString,
        fromJsVideoError
      )

    def generateVideoEffects(options: GenerateVideoEffectsOptions): String =
      generateVideoEffectsResult(options) match {
        case Right(value) => value
        case Left(err)    => throw new IllegalStateException(s"Video error ${err.tag}")
      }

    def multiImageGenerationResult(options: MultiImageGenerationOptions): Either[VideoError, String] =
      decodeResult(AdvancedModule.multiImageGeneration(toJsMultiImageGenerationOptions(options)))(
        _.toString,
        fromJsVideoError
      )

    def multiImageGeneration(options: MultiImageGenerationOptions): String =
      multiImageGenerationResult(options) match {
        case Right(value) => value
        case Left(err)    => throw new IllegalStateException(s"Video error ${err.tag}")
      }
  }

  // ----- Private interop ------------------------------------------------------------------

  private type JObj = js.Dictionary[js.Any]

  private def toJsMediaInput(input: MediaInput): JObj =
    input match {
      case MediaInput.Text(value) =>
        js.Dictionary[js.Any]("tag" -> "text", "val" -> value)
      case MediaInput.Image(value) =>
        js.Dictionary[js.Any]("tag" -> "image", "val" -> toJsReference(value))
      case MediaInput.Video(value) =>
        js.Dictionary[js.Any]("tag" -> "video", "val" -> toJsBaseVideo(value))
    }

  private def toJsReference(ref: Reference): JObj =
    js.Dictionary[js.Any](
      "data"   -> toJsInputImage(ref.data),
      "prompt" -> ref.prompt.fold[js.Any](js.undefined)(identity),
      "role"   -> ref.role.fold[js.Any](js.undefined)(_.tag)
    )

  private def toJsInputImage(img: InputImage): JObj =
    js.Dictionary[js.Any]("data" -> toJsMediaData(img.data))

  private def toJsBaseVideo(video: BaseVideo): JObj =
    js.Dictionary[js.Any]("data" -> toJsMediaData(video.data))

  private def toJsNarration(n: Narration): JObj =
    js.Dictionary[js.Any]("data" -> toJsMediaData(n.data))

  private def toJsMediaData(data: MediaData): JObj =
    data match {
      case MediaData.Url(value) =>
        js.Dictionary[js.Any]("tag" -> "url", "val" -> value)
      case MediaData.Bytes(value) =>
        js.Dictionary[js.Any]("tag" -> "bytes", "val" -> toJsRawBytes(value))
    }

  private def toJsRawBytes(raw: RawBytes): JObj =
    js.Dictionary[js.Any](
      "bytes"     -> toUint8Array(raw.bytes),
      "mime-type" -> raw.mimeType
    )

  private def toJsStaticMask(mask: StaticMask): JObj =
    js.Dictionary[js.Any]("mask" -> toJsInputImage(mask.mask))

  private def toJsDynamicMask(mask: DynamicMask): JObj =
    js.Dictionary[js.Any](
      "mask"         -> toJsInputImage(mask.mask),
      "trajectories" -> js.Array(mask.trajectories.map(toJsPosition): _*)
    )

  private def toJsPosition(pos: Position): JObj =
    js.Dictionary[js.Any]("x" -> pos.x, "y" -> pos.y)

  private def toJsCameraMovement(move: CameraMovement): JObj =
    move match {
      case CameraMovement.Simple(cfg) =>
        js.Dictionary[js.Any]("tag" -> "simple", "val" -> toJsCameraConfig(cfg))
      case CameraMovement.DownBack =>
        js.Dictionary[js.Any]("tag" -> "down-back")
      case CameraMovement.ForwardUp =>
        js.Dictionary[js.Any]("tag" -> "forward-up")
      case CameraMovement.RightTurnForward =>
        js.Dictionary[js.Any]("tag" -> "right-turn-forward")
      case CameraMovement.LeftTurnForward =>
        js.Dictionary[js.Any]("tag" -> "left-turn-forward")
    }

  private def toJsCameraConfig(cfg: CameraConfig): JObj =
    js.Dictionary[js.Any](
      "horizontal" -> cfg.horizontal,
      "vertical"   -> cfg.vertical,
      "pan"        -> cfg.pan,
      "tilt"       -> cfg.tilt,
      "zoom"       -> cfg.zoom,
      "roll"       -> cfg.roll
    )

  private def toJsGenerationConfig(cfg: GenerationConfig): JObj =
    js.Dictionary[js.Any](
      "negative-prompt"  -> cfg.negativePrompt.fold[js.Any](js.undefined)(identity),
      "seed"             -> cfg.seed.fold[js.Any](js.undefined)(toJsBigInt),
      "scheduler"        -> cfg.scheduler.fold[js.Any](js.undefined)(identity),
      "guidance-scale"   -> cfg.guidanceScale.fold[js.Any](js.undefined)(identity),
      "aspect-ratio"     -> cfg.aspectRatio.fold[js.Any](js.undefined)(_.tag),
      "duration-seconds" -> cfg.durationSeconds.fold[js.Any](js.undefined)(identity),
      "resolution"       -> cfg.resolution.fold[js.Any](js.undefined)(_.tag),
      "model"            -> cfg.model.fold[js.Any](js.undefined)(identity),
      "enable-audio"     -> cfg.enableAudio.fold[js.Any](js.undefined)(identity),
      "enhance-prompt"   -> cfg.enhancePrompt.fold[js.Any](js.undefined)(identity),
      "provider-options" -> cfg.providerOptions.fold[js.Any](js.undefined)(opts => js.Array(opts.map(toJsKv): _*)),
      "lastframe"        -> cfg.lastframe.fold[js.Any](js.undefined)(toJsInputImage),
      "static-mask"      -> cfg.staticMask.fold[js.Any](js.undefined)(toJsStaticMask),
      "dynamic-mask"     -> cfg.dynamicMask.fold[js.Any](js.undefined)(toJsDynamicMask),
      "camera-control"   -> cfg.cameraControl.fold[js.Any](js.undefined)(toJsCameraMovement)
    )

  private def toJsKv(kv: Kv): JObj =
    js.Dictionary[js.Any]("key" -> kv.key, "value" -> kv.value)

  private def toJsTextToSpeech(tts: TextToSpeech): JObj =
    js.Dictionary[js.Any](
      "text"     -> tts.text,
      "voice-id" -> tts.voiceId,
      "language" -> tts.language.tag,
      "speed"    -> tts.speed
    )

  private def toJsAudioSource(source: AudioSource): JObj =
    source match {
      case AudioSource.FromText(value) =>
        js.Dictionary[js.Any]("tag" -> "from-text", "val" -> toJsTextToSpeech(value))
      case AudioSource.FromAudio(value) =>
        js.Dictionary[js.Any]("tag" -> "from-audio", "val" -> toJsNarration(value))
    }

  private def toJsEffectType(effect: EffectType): JObj =
    effect match {
      case EffectType.Single(value) =>
        js.Dictionary[js.Any]("tag" -> "single", "val" -> value.tag)
      case EffectType.Dual(value) =>
        js.Dictionary[js.Any]("tag" -> "dual", "val" -> toJsDualEffect(value))
    }

  private def toJsDualEffect(effect: DualEffect): JObj =
    js.Dictionary[js.Any](
      "effect"       -> effect.effect.tag,
      "second-image" -> toJsInputImage(effect.secondImage)
    )

  private def toJsLipSyncVideo(video: LipSyncVideo): JObj =
    video match {
      case LipSyncVideo.Video(value) =>
        js.Dictionary[js.Any]("tag" -> "video", "val" -> toJsBaseVideo(value))
      case LipSyncVideo.VideoId(value) =>
        js.Dictionary[js.Any]("tag" -> "video-id", "val" -> value)
    }

  private def toJsExtendVideoOptions(options: ExtendVideoOptions): JObj =
    js.Dictionary[js.Any](
      "video-id"         -> options.videoId,
      "prompt"           -> options.prompt.fold[js.Any](js.undefined)(identity),
      "negative-prompt"  -> options.negativePrompt.fold[js.Any](js.undefined)(identity),
      "cfg-scale"        -> options.cfgScale.fold[js.Any](js.undefined)(identity),
      "provider-options" -> options.providerOptions.fold[js.Any](js.undefined)(opts => js.Array(opts.map(toJsKv): _*))
    )

  private def toJsGenerateVideoEffectsOptions(options: GenerateVideoEffectsOptions): JObj =
    js.Dictionary[js.Any](
      "input"    -> toJsInputImage(options.input),
      "effect"   -> toJsEffectType(options.effect),
      "model"    -> options.model.fold[js.Any](js.undefined)(identity),
      "duration" -> options.duration.fold[js.Any](js.undefined)(identity),
      "mode"     -> options.mode.fold[js.Any](js.undefined)(identity)
    )

  private def toJsMultiImageGenerationOptions(options: MultiImageGenerationOptions): JObj =
    js.Dictionary[js.Any](
      "input-images" -> js.Array(options.inputImages.map(toJsInputImage): _*),
      "prompt"       -> options.prompt.fold[js.Any](js.undefined)(identity),
      "config"       -> toJsGenerationConfig(options.config)
    )

  private def fromJsVideoResult(raw: js.Dynamic): VideoResult = {
    val obj    = raw.asInstanceOf[JObj]
    val status = optionDynamic(obj.getOrElse("status", null)).map(fromJsJobStatus).getOrElse(JobStatus.Pending)
    val videos = optionDynamic(obj.getOrElse("videos", null)).map(asArray).map(_.toList.map(fromJsVideo))
    VideoResult(status, videos)
  }

  private def fromJsVideo(raw: js.Dynamic): Video = {
    val obj = raw.asInstanceOf[JObj]
    Video(
      uri = obj.get("uri").map(_.toString),
      base64Bytes = obj.get("base64-bytes").map(asArray).map(toByteArray),
      mimeType = obj.getOrElse("mime-type", "").toString,
      width = obj.get("width").map(_.toString.toInt),
      height = obj.get("height").map(_.toString.toInt),
      fps = obj.get("fps").map(_.toString.toFloat),
      durationSeconds = obj.get("duration-seconds").map(_.toString.toFloat),
      generationId = obj.get("generation-id").map(_.toString)
    )
  }

  private def fromJsJobStatus(raw: js.Dynamic): JobStatus = {
    val tag = tagOf(raw)
    tag match {
      case "pending"   => JobStatus.Pending
      case "running"   => JobStatus.Running
      case "succeeded" => JobStatus.Succeeded
      case "failed"    => JobStatus.Failed(valOf(raw).toString)
      case _           => JobStatus.Pending
    }
  }

  private def fromJsVoiceList(raw: js.Dynamic): List[VoiceInfo] =
    asArray(raw).toList.map(fromJsVoiceInfo)

  private def fromJsVoiceInfo(raw: js.Dynamic): VoiceInfo = {
    val obj = raw.asInstanceOf[JObj]
    VoiceInfo(
      voiceId = obj.getOrElse("voice-id", "").toString,
      name = obj.getOrElse("name", "").toString,
      language = VoiceLanguage.fromTag(obj.getOrElse("language", "en").toString),
      previewUrl = obj.get("preview-url").map(_.toString)
    )
  }

  private def fromJsVideoError(raw: js.Dynamic): VideoError = {
    val tag   = tagOf(raw)
    val value = valOf(raw)
    tag match {
      case "invalid-input"       => VideoError.InvalidInput(value.toString)
      case "unsupported-feature" => VideoError.UnsupportedFeature(value.toString)
      case "quota-exceeded"      => VideoError.QuotaExceeded
      case "generation-failed"   => VideoError.GenerationFailed(value.toString)
      case "cancelled"           => VideoError.Cancelled
      case "internal-error"      => VideoError.InternalError(value.toString)
      case _                     => VideoError.InternalError(value.toString)
    }
  }

  private def decodeResult[A](
    raw: js.Dynamic
  )(ok: js.Dynamic => A, err: js.Dynamic => VideoError): Either[VideoError, A] = {
    val tag = tagOf(raw)
    tag match {
      case "ok" | "success" => Right(ok(valOf(raw)))
      case "err" | "error"  => Left(err(valOf(raw)))
      case _                => Right(ok(raw))
    }
  }

  private def tagOf(value: js.Dynamic): String = {
    val tag = value.asInstanceOf[js.Dynamic].selectDynamic("tag")
    if (js.isUndefined(tag) || tag == null) "" else tag.toString
  }

  private def valOf(value: js.Dynamic): js.Dynamic = {
    val dyn = value.asInstanceOf[js.Dynamic]
    if (!js.isUndefined(dyn.selectDynamic("val"))) dyn.selectDynamic("val").asInstanceOf[js.Dynamic]
    else if (!js.isUndefined(dyn.selectDynamic("value"))) dyn.selectDynamic("value").asInstanceOf[js.Dynamic]
    else dyn
  }

  private def asArray(value: js.Any): js.Array[js.Dynamic] =
    value.asInstanceOf[js.Array[js.Dynamic]]

  private def optionDynamic(value: js.Any): Option[js.Dynamic] =
    if (value == null || js.isUndefined(value)) None
    else {
      val dyn = value.asInstanceOf[js.Dynamic]
      val tag = dyn.selectDynamic("tag")
      if (!js.isUndefined(tag) && tag != null) {
        tag.toString match {
          case "some" => Some(dyn.selectDynamic("val").asInstanceOf[js.Dynamic])
          case "none" => None
          case _      => Some(dyn)
        }
      } else Some(dyn)
    }

  private def toUint8Array(bytes: Array[Byte]): Uint8Array = {
    val array = new Uint8Array(bytes.length)
    var i     = 0
    while (i < bytes.length) {
      array(i) = (bytes(i) & 0xff).toShort
      i += 1
    }
    array
  }

  private def toByteArray(value: js.Array[js.Dynamic]): Array[Byte] =
    value.map(_.toString.toInt.toByte).toArray

  private def toJsBigInt(value: BigInt): js.BigInt =
    js.BigInt(value.toString)

  @js.native
  @JSImport("golem:video-generation/video-generation@1.0.0", JSImport.Namespace)
  private object VideoModule extends js.Object {
    def generate(input: js.Dictionary[js.Any], config: js.Dictionary[js.Any]): js.Dynamic = js.native
    def poll(jobId: String): js.Dynamic                                                   = js.native
    def cancel(jobId: String): js.Dynamic                                                 = js.native
  }

  @js.native
  @JSImport("golem:video-generation/lip-sync@1.0.0", JSImport.Namespace)
  private object LipSyncModule extends js.Object {
    def generateLipSync(video: js.Dictionary[js.Any], audio: js.Dictionary[js.Any]): js.Dynamic = js.native
    def listVoices(language: js.Any): js.Dynamic                                                = js.native
  }

  @js.native
  @JSImport("golem:video-generation/advanced@1.0.0", JSImport.Namespace)
  private object AdvancedModule extends js.Object {
    def extendVideo(options: js.Dictionary[js.Any]): js.Dynamic          = js.native
    def upscaleVideo(input: js.Dictionary[js.Any]): js.Dynamic           = js.native
    def generateVideoEffects(options: js.Dictionary[js.Any]): js.Dynamic = js.native
    def multiImageGeneration(options: js.Dictionary[js.Any]): js.Dynamic = js.native
  }
}
