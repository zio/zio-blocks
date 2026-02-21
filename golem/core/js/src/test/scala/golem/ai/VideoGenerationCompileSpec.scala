package golem.ai

import org.scalatest.funsuite.AnyFunSuite

final class VideoGenerationCompileSpec extends AnyFunSuite {
  import VideoGeneration._

  test("VideoError — all 6 variants") {
    val errors: List[VideoError] = List(
      VideoError.InvalidInput("bad"),
      VideoError.UnsupportedFeature("nope"),
      VideoError.QuotaExceeded,
      VideoError.GenerationFailed("error"),
      VideoError.Cancelled,
      VideoError.InternalError("oops")
    )
    assert(errors.size == 6)
  }

  test("VideoError pattern match compiles") {
    def describe(e: VideoError): String = e match {
      case VideoError.InvalidInput(m)       => s"input($m)"
      case VideoError.UnsupportedFeature(m) => s"unsupported($m)"
      case VideoError.QuotaExceeded         => "quota"
      case VideoError.GenerationFailed(m)   => s"failed($m)"
      case VideoError.Cancelled             => "cancelled"
      case VideoError.InternalError(m)      => s"internal($m)"
    }
    assert(describe(VideoError.QuotaExceeded) == "quota")
  }

  test("MediaInput — all 3 variants") {
    val inputs: List[MediaInput] = List(
      MediaInput.Text("describe a sunset"),
      MediaInput.Image(Reference(InputImage(MediaData.Url("http://img")), None, None)),
      MediaInput.Video(BaseVideo(MediaData.Bytes(RawBytes(Array[Byte](1), "video/mp4"))))
    )
    assert(inputs.size == 3)
  }

  test("ImageRole — 2 variants and fromTag") {
    assert(ImageRole.fromTag("first") == ImageRole.First)
    assert(ImageRole.fromTag("last") == ImageRole.Last)
  }

  test("MediaData — Url and Bytes variants") {
    val url: MediaData   = MediaData.Url("http://video.mp4")
    val bytes: MediaData = MediaData.Bytes(RawBytes(Array[Byte](1, 2, 3), "video/mp4"))
    assert(url.isInstanceOf[MediaData.Url])
    assert(bytes.isInstanceOf[MediaData.Bytes])
  }

  test("Reference, InputImage, BaseVideo, Narration construction") {
    val ref  = Reference(InputImage(MediaData.Url("http://img")), Some("a prompt"), Some(ImageRole.First))
    val vid  = BaseVideo(MediaData.Url("http://vid"))
    val narr = Narration(MediaData.Bytes(RawBytes(Array.empty[Byte], "audio/wav")))
    assert(ref.prompt.contains("a prompt"))
    assert(vid.data.isInstanceOf[MediaData.Url])
    assert(narr.data.isInstanceOf[MediaData.Bytes])
  }

  test("Mask types compile") {
    val static  = StaticMask(InputImage(MediaData.Url("http://mask")))
    val dynamic = DynamicMask(InputImage(MediaData.Url("http://mask")), List(Position(10, 20), Position(30, 40)))
    assert(static.mask.data.isInstanceOf[MediaData.Url])
    assert(dynamic.trajectories.size == 2)
  }

  test("CameraMovement — all 5 variants") {
    val movements: List[CameraMovement] = List(
      CameraMovement.Simple(CameraConfig(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f)),
      CameraMovement.DownBack,
      CameraMovement.ForwardUp,
      CameraMovement.RightTurnForward,
      CameraMovement.LeftTurnForward
    )
    assert(movements.size == 5)
  }

  test("CameraConfig construction") {
    val config = CameraConfig(
      horizontal = 1.0f,
      vertical = 2.0f,
      pan = 0.5f,
      tilt = 0.3f,
      zoom = 1.5f,
      roll = 0.0f
    )
    assert(config.horizontal == 1.0f)
    assert(config.zoom == 1.5f)
  }

  test("GenerationConfig construction with all optional fields") {
    val config = GenerationConfig(
      negativePrompt = Some("no watermark"),
      seed = Some(BigInt(42)),
      scheduler = Some("ddim"),
      guidanceScale = Some(7.5f),
      aspectRatio = Some(AspectRatio.Landscape),
      durationSeconds = Some(5.0f),
      resolution = Some(Resolution.Hd),
      model = Some("model-1"),
      enableAudio = Some(true),
      enhancePrompt = Some(false),
      providerOptions = Some(List(Kv("k", "v"))),
      lastframe = None,
      staticMask = None,
      dynamicMask = None,
      cameraControl = Some(CameraMovement.ForwardUp)
    )
    assert(config.model.contains("model-1"))
    assert(config.durationSeconds.contains(5.0f))
  }

  test("GenerationConfig with None defaults") {
    val config = GenerationConfig()
    assert(config.model.isEmpty)
    assert(config.cameraControl.isEmpty)
  }

  test("AspectRatio — all 4 variants and fromTag") {
    val ratios = List(AspectRatio.Square, AspectRatio.Portrait, AspectRatio.Landscape, AspectRatio.Cinema)
    assert(ratios.size == 4)
    ratios.foreach(r => assert(AspectRatio.fromTag(r.tag) eq r))
  }

  test("Resolution — all 4 variants and fromTag") {
    val resolutions = List(Resolution.Sd, Resolution.Hd, Resolution.Fhd, Resolution.Uhd)
    assert(resolutions.size == 4)
    resolutions.foreach(r => assert(Resolution.fromTag(r.tag) eq r))
  }

  test("JobStatus — all 4 variants") {
    val statuses: List[JobStatus] = List(
      JobStatus.Pending,
      JobStatus.Running,
      JobStatus.Succeeded,
      JobStatus.Failed("error")
    )
    assert(statuses.size == 4)
  }

  test("VideoResult construction") {
    val result = VideoResult(
      status = JobStatus.Succeeded,
      videos = Some(
        List(
          Video(
            uri = Some("http://result.mp4"),
            base64Bytes = None,
            mimeType = "video/mp4",
            width = Some(1920),
            height = Some(1080),
            fps = Some(24.0f),
            durationSeconds = Some(5.0f),
            generationId = Some("gen-1")
          )
        )
      )
    )
    assert(result.status == JobStatus.Succeeded)
    assert(result.videos.exists(_.nonEmpty))
  }

  test("Video construction with all fields") {
    val video = Video(
      uri = Some("http://video.mp4"),
      base64Bytes = Some(Array[Byte](1, 2)),
      mimeType = "video/mp4",
      width = Some(1280),
      height = Some(720),
      fps = Some(30.0f),
      durationSeconds = Some(3.0f),
      generationId = None
    )
    assert(video.mimeType == "video/mp4")
    assert(video.width.contains(1280))
  }

  test("VoiceLanguage — variants and fromTag") {
    assert(VoiceLanguage.fromTag("en") == VoiceLanguage.En)
    assert(VoiceLanguage.fromTag("zh") == VoiceLanguage.Zh)
  }

  test("AudioSource — FromText and FromAudio variants") {
    val tts: AudioSource =
      AudioSource.FromText(TextToSpeech("hello", "voice-1", VoiceLanguage.En, 1.0f))
    val audio: AudioSource =
      AudioSource.FromAudio(Narration(MediaData.Url("http://audio.wav")))
    assert(tts.isInstanceOf[AudioSource.FromText])
    assert(audio.isInstanceOf[AudioSource.FromAudio])
  }

  test("VoiceInfo construction") {
    val info = VoiceInfo("voice-1", "Alice", VoiceLanguage.En, Some("http://preview.wav"))
    assert(info.name == "Alice")
  }

  test("SingleImageEffects — all 7 variants") {
    val effects = List(
      SingleImageEffects.Bloombloom,
      SingleImageEffects.Dizzydizzy,
      SingleImageEffects.Fuzzyfuzzy,
      SingleImageEffects.Squish,
      SingleImageEffects.Expansion,
      SingleImageEffects.AnimeFigure,
      SingleImageEffects.Rocketrocket
    )
    assert(effects.size == 7)
    effects.foreach(e => assert(SingleImageEffects.fromTag(e.tag) eq e))
  }

  test("DualImageEffects — all 3 variants") {
    val effects = List(DualImageEffects.Hug, DualImageEffects.Kiss, DualImageEffects.HeartGesture)
    assert(effects.size == 3)
    effects.foreach(e => assert(DualImageEffects.fromTag(e.tag) eq e))
  }

  test("EffectType — Single and Dual variants") {
    val single: EffectType = EffectType.Single(SingleImageEffects.Squish)
    val dual: EffectType   = EffectType.Dual(
      DualEffect(DualImageEffects.Hug, InputImage(MediaData.Url("http://img2")))
    )
    assert(single.isInstanceOf[EffectType.Single])
    assert(dual.isInstanceOf[EffectType.Dual])
  }

  test("LipSyncVideo — Video and VideoId variants") {
    val vid: LipSyncVideo   = LipSyncVideo.Video(BaseVideo(MediaData.Url("http://v")))
    val vidId: LipSyncVideo = LipSyncVideo.VideoId("video-123")
    assert(vid.isInstanceOf[LipSyncVideo.Video])
    assert(vidId.isInstanceOf[LipSyncVideo.VideoId])
  }
}
