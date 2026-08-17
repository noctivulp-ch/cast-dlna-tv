package app.castdlna.tv.upnp

/**
 * Descriptores UPnP: `device.xml` y los SCPD de los tres servicios.
 *
 * Los SCPD se generan desde tablas compactas en vez de escribirse a mano: la
 * lista de acciones es también la fuente de verdad de lo que el receptor
 * anuncia, así que declaración e implementación no pueden divergir en
 * silencio.
 */
object Servicios {
    const val AVT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RCS = "urn:schemas-upnp-org:service:RenderingControl:1"
    const val CMS = "urn:schemas-upnp-org:service:ConnectionManager:1"
}

private const val IN = "in"
private const val OUT = "out"

private data class Arg(val nombre: String, val direccion: String, val variable: String)

private data class Variable(
    val nombre: String,
    val tipo: String,
    val eventos: Boolean,
    val valores: List<String>? = null,
    val rango: Triple<Int, Int, Int>? = null,
)

private val ACCIONES_AVT: Map<String, List<Arg>> = mapOf(
    "SetAVTransportURI" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("CurrentURI", IN, "AVTransportURI"),
        Arg("CurrentURIMetaData", IN, "AVTransportURIMetaData"),
    ),
    "SetNextAVTransportURI" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("NextURI", IN, "NextAVTransportURI"),
        Arg("NextURIMetaData", IN, "NextAVTransportURIMetaData"),
    ),
    "GetMediaInfo" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("NrTracks", OUT, "NumberOfTracks"),
        Arg("MediaDuration", OUT, "CurrentMediaDuration"),
        Arg("CurrentURI", OUT, "AVTransportURI"),
        Arg("CurrentURIMetaData", OUT, "AVTransportURIMetaData"),
        Arg("NextURI", OUT, "NextAVTransportURI"),
        Arg("NextURIMetaData", OUT, "NextAVTransportURIMetaData"),
        Arg("PlayMedium", OUT, "PlaybackStorageMedium"),
        Arg("RecordMedium", OUT, "RecordStorageMedium"),
        Arg("WriteStatus", OUT, "RecordMediumWriteStatus"),
    ),
    "GetMediaInfo_Ext" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("CurrentType", OUT, "CurrentMediaCategory"),
        Arg("NrTracks", OUT, "NumberOfTracks"),
        Arg("MediaDuration", OUT, "CurrentMediaDuration"),
        Arg("CurrentURI", OUT, "AVTransportURI"),
        Arg("CurrentURIMetaData", OUT, "AVTransportURIMetaData"),
        Arg("NextURI", OUT, "NextAVTransportURI"),
        Arg("NextURIMetaData", OUT, "NextAVTransportURIMetaData"),
        Arg("PlayMedium", OUT, "PlaybackStorageMedium"),
        Arg("RecordMedium", OUT, "RecordStorageMedium"),
        Arg("WriteStatus", OUT, "RecordMediumWriteStatus"),
    ),
    "GetTransportInfo" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("CurrentTransportState", OUT, "TransportState"),
        Arg("CurrentTransportStatus", OUT, "TransportStatus"),
        Arg("CurrentSpeed", OUT, "TransportPlaySpeed"),
    ),
    "GetPositionInfo" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Track", OUT, "CurrentTrack"),
        Arg("TrackDuration", OUT, "CurrentTrackDuration"),
        Arg("TrackMetaData", OUT, "CurrentTrackMetaData"),
        Arg("TrackURI", OUT, "CurrentTrackURI"),
        Arg("RelTime", OUT, "RelativeTimePosition"),
        Arg("AbsTime", OUT, "AbsoluteTimePosition"),
        Arg("RelCount", OUT, "RelativeCounterPosition"),
        Arg("AbsCount", OUT, "AbsoluteCounterPosition"),
    ),
    "GetDeviceCapabilities" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("PlayMedia", OUT, "PossiblePlaybackStorageMedia"),
        Arg("RecMedia", OUT, "PossibleRecordStorageMedia"),
        Arg("RecQualityModes", OUT, "PossibleRecordQualityModes"),
    ),
    "GetTransportSettings" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("PlayMode", OUT, "CurrentPlayMode"),
        Arg("RecQualityMode", OUT, "CurrentRecordQualityMode"),
    ),
    "Stop" to listOf(Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID")),
    "Play" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Speed", IN, "TransportPlaySpeed"),
    ),
    "Pause" to listOf(Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID")),
    "Seek" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Unit", IN, "A_ARG_TYPE_SeekMode"),
        Arg("Target", IN, "A_ARG_TYPE_SeekTarget"),
    ),
    "Next" to listOf(Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID")),
    "Previous" to listOf(Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID")),
    "SetPlayMode" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("NewPlayMode", IN, "CurrentPlayMode"),
    ),
    "GetCurrentTransportActions" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Actions", OUT, "CurrentTransportActions"),
    ),
    "X_DLNA_GetBytePositionInfo" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("TrackSize", OUT, "X_DLNA_A_ARG_TYPE_TrackSize"),
        Arg("RelByte", OUT, "X_DLNA_A_ARG_TYPE_RelByte"),
        Arg("AbsByte", OUT, "X_DLNA_A_ARG_TYPE_AbsByte"),
    ),
)

private val VARIABLES_AVT = listOf(
    Variable("TransportState", "string", true,
        listOf("STOPPED", "PLAYING", "PAUSED_PLAYBACK", "TRANSITIONING", "NO_MEDIA_PRESENT")),
    Variable("TransportStatus", "string", true, listOf("OK", "ERROR_OCCURRED")),
    Variable("PlaybackStorageMedium", "string", true, listOf("NONE", "NETWORK", "HDD", "UNKNOWN")),
    Variable("RecordStorageMedium", "string", true, listOf("NOT_IMPLEMENTED")),
    Variable("PossiblePlaybackStorageMedia", "string", true),
    Variable("PossibleRecordStorageMedia", "string", true),
    Variable("CurrentPlayMode", "string", true,
        listOf("NORMAL", "REPEAT_ONE", "REPEAT_ALL", "SHUFFLE")),
    Variable("TransportPlaySpeed", "string", true, listOf("1")),
    Variable("RecordMediumWriteStatus", "string", true, listOf("NOT_IMPLEMENTED")),
    Variable("CurrentRecordQualityMode", "string", true, listOf("NOT_IMPLEMENTED")),
    Variable("PossibleRecordQualityModes", "string", true),
    Variable("NumberOfTracks", "ui4", true, rango = Triple(0, 65535, 1)),
    Variable("CurrentTrack", "ui4", true, rango = Triple(0, 65535, 1)),
    Variable("CurrentTrackDuration", "string", true),
    Variable("CurrentMediaDuration", "string", true),
    Variable("CurrentTrackMetaData", "string", true),
    Variable("CurrentTrackURI", "string", true),
    Variable("AVTransportURI", "string", true),
    Variable("AVTransportURIMetaData", "string", true),
    Variable("NextAVTransportURI", "string", true),
    Variable("NextAVTransportURIMetaData", "string", true),
    Variable("CurrentTransportActions", "string", true),
    Variable("CurrentMediaCategory", "string", true,
        listOf("NO_MEDIA", "TRACK_AWARE", "TRACK_UNAWARE")),
    Variable("RelativeTimePosition", "string", false),
    Variable("AbsoluteTimePosition", "string", false),
    Variable("RelativeCounterPosition", "i4", false),
    Variable("AbsoluteCounterPosition", "i4", false),
    Variable("LastChange", "string", true),
    Variable("A_ARG_TYPE_SeekMode", "string", false,
        listOf("TRACK_NR", "REL_TIME", "ABS_TIME", "ABS_COUNT", "REL_COUNT")),
    Variable("A_ARG_TYPE_SeekTarget", "string", false),
    Variable("A_ARG_TYPE_InstanceID", "ui4", false),
    Variable("X_DLNA_A_ARG_TYPE_TrackSize", "string", false),
    Variable("X_DLNA_A_ARG_TYPE_RelByte", "string", false),
    Variable("X_DLNA_A_ARG_TYPE_AbsByte", "string", false),
)

private val ACCIONES_RCS: Map<String, List<Arg>> = mapOf(
    "ListPresets" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("CurrentPresetNameList", OUT, "PresetNameList"),
    ),
    "SelectPreset" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("PresetName", IN, "A_ARG_TYPE_PresetName"),
    ),
    "GetVolume" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Channel", IN, "A_ARG_TYPE_Channel"),
        Arg("CurrentVolume", OUT, "Volume"),
    ),
    "SetVolume" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Channel", IN, "A_ARG_TYPE_Channel"),
        Arg("DesiredVolume", IN, "Volume"),
    ),
    "GetVolumeDB" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Channel", IN, "A_ARG_TYPE_Channel"),
        Arg("CurrentVolume", OUT, "VolumeDB"),
    ),
    "SetVolumeDB" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Channel", IN, "A_ARG_TYPE_Channel"),
        Arg("DesiredVolume", IN, "VolumeDB"),
    ),
    "GetVolumeDBRange" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Channel", IN, "A_ARG_TYPE_Channel"),
        Arg("MinValue", OUT, "VolumeDB"),
        Arg("MaxValue", OUT, "VolumeDB"),
    ),
    "GetMute" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Channel", IN, "A_ARG_TYPE_Channel"),
        Arg("CurrentMute", OUT, "Mute"),
    ),
    "SetMute" to listOf(
        Arg("InstanceID", IN, "A_ARG_TYPE_InstanceID"),
        Arg("Channel", IN, "A_ARG_TYPE_Channel"),
        Arg("DesiredMute", IN, "Mute"),
    ),
)

private val VARIABLES_RCS = listOf(
    Variable("PresetNameList", "string", false),
    Variable("LastChange", "string", true),
    Variable("Mute", "boolean", false),
    Variable("Volume", "ui2", false, rango = Triple(0, 100, 1)),
    Variable("VolumeDB", "i2", false),
    Variable("A_ARG_TYPE_Channel", "string", false, listOf("Master")),
    Variable("A_ARG_TYPE_InstanceID", "ui4", false),
    Variable("A_ARG_TYPE_PresetName", "string", false, listOf("FactoryDefaults")),
)

private val ACCIONES_CMS: Map<String, List<Arg>> = mapOf(
    "GetProtocolInfo" to listOf(
        Arg("Source", OUT, "SourceProtocolInfo"),
        Arg("Sink", OUT, "SinkProtocolInfo"),
    ),
    "GetCurrentConnectionIDs" to listOf(
        Arg("ConnectionIDs", OUT, "CurrentConnectionIDs"),
    ),
    "GetCurrentConnectionInfo" to listOf(
        Arg("ConnectionID", IN, "A_ARG_TYPE_ConnectionID"),
        Arg("RcsID", OUT, "A_ARG_TYPE_RcsID"),
        Arg("AVTransportID", OUT, "A_ARG_TYPE_AVTransportID"),
        Arg("ProtocolInfo", OUT, "A_ARG_TYPE_ProtocolInfo"),
        Arg("PeerConnectionManager", OUT, "A_ARG_TYPE_ConnectionManager"),
        Arg("PeerConnectionID", OUT, "A_ARG_TYPE_ConnectionID"),
        Arg("Direction", OUT, "A_ARG_TYPE_Direction"),
        Arg("Status", OUT, "A_ARG_TYPE_ConnectionStatus"),
    ),
)

private val VARIABLES_CMS = listOf(
    Variable("SourceProtocolInfo", "string", true),
    Variable("SinkProtocolInfo", "string", true),
    Variable("CurrentConnectionIDs", "string", true),
    Variable("A_ARG_TYPE_ConnectionStatus", "string", false,
        listOf("OK", "ContentFormatMismatch", "InsufficientBandwidth",
            "UnreliableChannel", "Unknown")),
    Variable("A_ARG_TYPE_ConnectionManager", "string", false),
    Variable("A_ARG_TYPE_Direction", "string", false, listOf("Input", "Output")),
    Variable("A_ARG_TYPE_ProtocolInfo", "string", false),
    Variable("A_ARG_TYPE_ConnectionID", "i4", false),
    Variable("A_ARG_TYPE_AVTransportID", "i4", false),
    Variable("A_ARG_TYPE_RcsID", "i4", false),
)

private fun scpd(acciones: Map<String, List<Arg>>, variables: List<Variable>): String {
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="utf-8"?>""")
    sb.append("""<scpd xmlns="urn:schemas-upnp-org:service-1-0">""")
    sb.append("<specVersion><major>1</major><minor>0</minor></specVersion>")
    sb.append("<actionList>")
    for ((nombre, args) in acciones) {
        sb.append("<action><name>").append(nombre).append("</name><argumentList>")
        for (a in args) {
            sb.append("<argument><name>").append(a.nombre)
                .append("</name><direction>").append(a.direccion)
                .append("</direction><relatedStateVariable>").append(a.variable)
                .append("</relatedStateVariable></argument>")
        }
        sb.append("</argumentList></action>")
    }
    sb.append("</actionList><serviceStateTable>")
    for (v in variables) {
        sb.append("""<stateVariable sendEvents="""")
            .append(if (v.eventos) "yes" else "no").append("""">""")
            .append("<name>").append(v.nombre).append("</name>")
            .append("<dataType>").append(v.tipo).append("</dataType>")
        v.valores?.let { valores ->
            sb.append("<allowedValueList>")
            valores.forEach { sb.append("<allowedValue>").append(it).append("</allowedValue>") }
            sb.append("</allowedValueList>")
        }
        v.rango?.let { (min, max, paso) ->
            sb.append("<allowedValueRange><minimum>").append(min)
                .append("</minimum><maximum>").append(max)
                .append("</maximum><step>").append(paso).append("</step></allowedValueRange>")
        }
        sb.append("</stateVariable>")
    }
    sb.append("</serviceStateTable></scpd>")
    return sb.toString()
}

val SCPD_AVT: String by lazy { scpd(ACCIONES_AVT, VARIABLES_AVT) }
val SCPD_RCS: String by lazy { scpd(ACCIONES_RCS, VARIABLES_RCS) }
val SCPD_CMS: String by lazy { scpd(ACCIONES_CMS, VARIABLES_CMS) }

/**
 * Formatos que el receptor acepta.
 *
 * Los de subtítulo van primero a propósito: hay controladores que solo
 * inspeccionan el principio de la lista para decidir si hay soporte, y con una
 * lista corta llegan a rechazar el contenido antes de intentar el
 * `SetAVTransportURI`.
 */
private val MIMES = listOf(
    "smi/caption", "text/srt", "text/vtt", "text/sami", "text/ssa",
    "application/x-subrip", "application/smil", "application/ttml+xml",
    "video/mp4", "video/x-matroska", "video/webm", "video/mpeg", "video/avi",
    "video/x-msvideo", "video/quicktime", "video/x-flv", "video/3gpp",
    "video/x-ms-wmv", "video/x-ms-asf", "video/mp2t", "video/MP2T",
    "video/vnd.dlna.mpeg-tts", "video/x-m4v", "video/ogg", "video/divx",
    "application/x-mpegURL", "application/vnd.apple.mpegurl",
    "application/dash+xml", "application/octet-stream",
    "audio/mpeg", "audio/mp4", "audio/aac", "audio/x-aac", "audio/flac",
    "audio/x-flac", "audio/ogg", "audio/vorbis", "audio/opus", "audio/wav",
    "audio/x-wav", "audio/L16", "audio/x-ms-wma", "audio/mpegurl",
    "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp",
)

val SINK_PROTOCOL_INFO: String =
    MIMES.joinToString(",") { "http-get:*:$it:*" } + ",rtsp-rtp-udp:*:*:*"

/**
 * Descriptor del dispositivo.
 *
 * Los marcadores del final no son adorno. `sec:ProductCap` es lo que miran los
 * controladores que mandan los subtítulos por `sec:CaptionInfoEx` —Web Video
 * Caster entre ellos— para decidir si el receptor los admite; sin él anuncian
 * que el dispositivo «no tiene soporte para subtítulos» aunque el Sink los
 * declare. Los `pnpx`/`df` son los que espera Windows.
 */
fun deviceXml(nombre: String, udn: String, baseUrl: String): String = """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0" xmlns:sec="http://www.sec.co.kr/dlna" xmlns:pnpx="http://schemas.microsoft.com/windows/pnpx/2005/11" xmlns:df="http://schemas.microsoft.com/windows/2008/09/devicefoundation">
  <specVersion><major>1</major><minor>0</minor></specVersion>
  <device>
    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
    <dlna:X_DLNADOC xmlns:dlna="urn:schemas-dlna-org:device-1-0">DMR-1.50</dlna:X_DLNADOC>
    <pnpx:X_compatibleId>MS_DigitalMediaDeviceClass_DMR_V001</pnpx:X_compatibleId>
    <pnpx:X_deviceCategory>MediaDevices</pnpx:X_deviceCategory>
    <df:X_deviceCategory>Multimedia.DMR</df:X_deviceCategory>
    <friendlyName>$nombre</friendlyName>
    <manufacturer>cast-dlna</manufacturer>
    <manufacturerURL>https://github.com/</manufacturerURL>
    <modelDescription>Receptor DLNA para Android TV</modelDescription>
    <modelName>cast-dlna-tv</modelName>
    <modelNumber>1.0</modelNumber>
    <modelURL>https://github.com/</modelURL>
    <serialNumber>${udn.takeLast(12)}</serialNumber>
    <UDN>$udn</UDN>
    <iconList>
      <icon><mimetype>image/png</mimetype><width>48</width><height>48</height>
        <depth>24</depth><url>/icon.png</url></icon>
    </iconList>
    <serviceList>
      <service>
        <serviceType>${Servicios.AVT}</serviceType>
        <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
        <SCPDURL>/scpd/avt.xml</SCPDURL>
        <controlURL>/ctrl/avt</controlURL>
        <eventSubURL>/evt/avt</eventSubURL>
      </service>
      <service>
        <serviceType>${Servicios.RCS}</serviceType>
        <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
        <SCPDURL>/scpd/rcs.xml</SCPDURL>
        <controlURL>/ctrl/rcs</controlURL>
        <eventSubURL>/evt/rcs</eventSubURL>
      </service>
      <service>
        <serviceType>${Servicios.CMS}</serviceType>
        <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
        <SCPDURL>/scpd/cms.xml</SCPDURL>
        <controlURL>/ctrl/cms</controlURL>
        <eventSubURL>/evt/cms</eventSubURL>
      </service>
    </serviceList>
    <sec:ProductCap>Y2020,WebURIPlayable,SeekTRACK_NR,NavigateInPause,Captions</sec:ProductCap>
    <sec:X_ProductCap>Y2020,WebURIPlayable,SeekTRACK_NR,NavigateInPause,Captions</sec:X_ProductCap>
    <pnpx:X_hardwareId>VEN_0105&amp;DEV_VD0001</pnpx:X_hardwareId>
    <presentationURL>$baseUrl/</presentationURL>
  </device>
</root>"""
