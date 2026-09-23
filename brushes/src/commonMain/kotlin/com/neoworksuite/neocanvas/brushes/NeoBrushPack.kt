package com.neoworksuite.neocanvas.brushes

data class NeoBrushPack(
    val manifest: NeoBrushPackManifest,
    val brushes: LinkedHashMap<String, BrushDefinition>,
    val assets: LinkedHashMap<String, ByteArray> = linkedMapOf(),
    val previews: LinkedHashMap<String, ByteArray> = linkedMapOf(),
    val artwork: LinkedHashMap<String, ByteArray> = linkedMapOf(),
) {
    init {
        require(brushes.keys.toList() == manifest.brushIds)
        require(brushes.all { (id, brush) -> id == brush.id })
    }
}

object NeoBrushPackCodec {
    fun encode(pack: NeoBrushPack): ByteArray {
        val members = linkedMapOf<String, ByteArray>()
        pack.manifest.brushIds.forEach { id -> members["brushes/$id.neobrush"] = NeoBrushCodec.encode(pack.brushes.getValue(id)) }
        pack.assets.forEach { (name, bytes) -> members["assets/$name"] = bytes }
        pack.previews.forEach { (name, bytes) -> members["previews/$name"] = bytes }
        pack.artwork.forEach { (name, bytes) -> members["artwork/$name"] = bytes }
        val entries = members.map { (path, bytes) -> path to Sha256.hex(bytes) }
        members["manifest.json"] = manifestJson(pack.manifest, entries).encodeToByteArray()
        val ordered = linkedMapOf("manifest.json" to members.getValue("manifest.json"))
        members.filterKeys { it != "manifest.json" }.forEach(ordered::put)
        ordered["signature/manifest.sha256"] = Sha256.hex(ordered.getValue("manifest.json")).encodeToByteArray()
        return ZipStoreCodec.encode(ordered)
    }

    fun decode(bytes: ByteArray, appVersion: String): NeoBrushPack {
        try {
            val members = ZipStoreCodec.decode(bytes)
            val manifestBytes = members["manifest.json"] ?: invalid("Manifest is missing.")
            val signature = members["signature/manifest.sha256"]?.decodeToString() ?: invalid("Manifest checksum is missing.")
            if (signature != Sha256.hex(manifestBytes)) checksum()
            val parsed = parseManifest(manifestBytes.decodeToString())
            if (compareVersions(appVersion, parsed.manifest.minimumAppVersion) < 0)
                throw PackValidationException(PackValidationError.IncompatibleVersion, "A newer NeoCanvas version is required.")
            val declared = linkedSetOf("manifest.json", "signature/manifest.sha256")
            val brushes = linkedMapOf<String, BrushDefinition>(); val assets=linkedMapOf<String,ByteArray>();val previews=linkedMapOf<String,ByteArray>();val artwork=linkedMapOf<String,ByteArray>()
            parsed.entries.forEach { (path, hash) ->
                val data = members[path] ?: invalid("Declared pack entry is missing.")
                if (Sha256.hex(data) != hash) checksum(); declared += path
                when { path.startsWith("brushes/") -> { val brush=NeoBrushCodec.decode(data); brushes[brush.id]=brush }
                    path.startsWith("assets/") -> assets[path.removePrefix("assets/")]=data
                    path.startsWith("previews/") -> previews[path.removePrefix("previews/")]=data
                    path.startsWith("artwork/") -> artwork[path.removePrefix("artwork/")]=data
                    else -> throw PackValidationException(PackValidationError.UnsupportedEntry,"Unsupported pack entry.") }
            }
            if (members.keys != declared) throw PackValidationException(PackValidationError.UnsupportedEntry,"Pack contains undeclared entries.")
            if (brushes.keys.toList() != parsed.manifest.brushIds) invalid("Brush order does not match the manifest.")
            return NeoBrushPack(parsed.manifest, LinkedHashMap(brushes), LinkedHashMap(assets), LinkedHashMap(previews), LinkedHashMap(artwork))
        } catch (e: PackValidationException) { throw e }
        catch (_: IllegalArgumentException) { invalid("Invalid brush pack manifest.") }
    }

    private data class Parsed(val manifest: NeoBrushPackManifest,val entries:List<Pair<String,String>>)
    private fun manifestJson(m:NeoBrushPackManifest,entries:List<Pair<String,String>>) = buildString {
        append("{\"schemaVersion\":1,\"id\":\"").append(esc(m.id)).append("\",\"version\":\"").append(esc(m.version))
        append("\",\"name\":\"").append(esc(m.name)).append("\",\"summary\":\"").append(esc(m.summary)).append("\",\"author\":\"").append(esc(m.author))
        append("\",\"website\":\"").append(esc(m.website)).append("\",\"licence\":\"").append(esc(m.licence)).append("\",\"minimumAppVersion\":\"").append(esc(m.minimumAppVersion))
        append("\",\"brushIds\":[").append(m.brushIds.joinToString(","){"\"${esc(it)}\""}).append("],\"entries\":[")
        append(entries.joinToString(","){(p,h)->"{\"path\":\"${esc(p)}\",\"sha256\":\"$h\"}"}).append("]}")
    }
    private fun parseManifest(json:String):Parsed {
        fun string(key:String)=Regex("\\\"$key\\\":\\\"((?:\\\\.|[^\\\"])*)\\\"").find(json)?.groupValues?.get(1)?.let(::unesc)?:invalid("Manifest field is missing.")
        val schema=Regex("\\\"schemaVersion\\\":([0-9]+)").find(json)?.groupValues?.get(1)?.toIntOrNull()?:invalid("Manifest schema is missing.")
        if(schema!=1)invalid("Unsupported manifest schema.")
        val idsBody=Regex("\\\"brushIds\\\":\\[(.*?)]").find(json)?.groupValues?.get(1)?:invalid("Brush list is missing.")
        val ids=Regex("\\\"((?:\\\\.|[^\\\"])*)\\\"").findAll(idsBody).map{unesc(it.groupValues[1])}.toList()
        val entries=Regex("\\{\\\"path\\\":\\\"((?:\\\\.|[^\\\"])*)\\\",\\\"sha256\\\":\\\"([0-9a-f]{64})\\\"}").findAll(json).map{unesc(it.groupValues[1]) to it.groupValues[2]}.toList()
        return Parsed(NeoBrushPackManifest(string("id"),string("version"),string("name"),string("summary"),string("author"),string("website"),string("licence"),string("minimumAppVersion"),ids,schema),entries)
    }
    private fun esc(s:String)=buildString{s.forEach{when(it){'\\'->append("\\\\");'\"'->append("\\\"");'\n'->append("\\n");else->append(it)}}}
    private fun unesc(s:String)=s.replace("\\n","\n").replace("\\\"","\"").replace("\\\\","\\")
    private fun compareVersions(a:String,b:String):Int{val x=a.split('.').map{it.toIntOrNull()?:0};val y=b.split('.').map{it.toIntOrNull()?:0};repeat(3){if(x[it]!=y[it])return x[it].compareTo(y[it])};return 0}
    private fun checksum():Nothing=throw PackValidationException(PackValidationError.ChecksumMismatch,"Brush pack checksum failed.")
    private fun invalid(message:String):Nothing=throw PackValidationException(PackValidationError.InvalidManifest,message)
}
