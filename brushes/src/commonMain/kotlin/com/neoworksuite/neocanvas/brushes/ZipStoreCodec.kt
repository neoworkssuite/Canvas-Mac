package com.neoworksuite.neocanvas.brushes

enum class PackValidationError { InvalidArchive, UnsafePath, UnsupportedEntry, TooLarge, TooManyEntries, ChecksumMismatch, InvalidManifest, IncompatibleVersion }
class PackValidationException(val error: PackValidationError, message: String) : IllegalArgumentException(message)

object ZipStoreCodec {
    const val MAX_ARCHIVE = 25 * 1024 * 1024
    const val MAX_UNPACKED = 75 * 1024 * 1024
    const val MAX_ENTRIES = 250
    fun encode(entries: Map<String, ByteArray>): ByteArray {
        if (entries.size > MAX_ENTRIES) fail(PackValidationError.TooManyEntries,"Pack contains too many entries.")
        val out=Bytes(); val records=mutableListOf<Record>(); var total=0L
        entries.forEach { (raw,data) -> val name=validateName(raw); total+=data.size; if(total>MAX_UNPACKED) fail(PackValidationError.TooLarge,"Pack is too large."); val nb=name.encodeToByteArray(); val crc=crc32(data); val off=out.size
            out.i(0x04034b50);out.s(20);out.s(0);out.s(0);out.s(0);out.s(0);out.i(crc);out.i(data.size);out.i(data.size);out.s(nb.size);out.s(0);out.add(nb);out.add(data);records+=Record(name,data.size,crc,off) }
        val central=out.size; records.forEach { r->val nb=r.name.encodeToByteArray();out.i(0x02014b50);out.s(20);out.s(20);out.s(0);out.s(0);out.s(0);out.s(0);out.i(r.crc);out.i(r.size);out.i(r.size);out.s(nb.size);out.s(0);out.s(0);out.s(0);out.s(0);out.i(0);out.i(r.offset);out.add(nb)}
        val cs=out.size-central;out.i(0x06054b50);out.s(0);out.s(0);out.s(records.size);out.s(records.size);out.i(cs);out.i(central);out.s(0)
        return out.array().also { if(it.size>MAX_ARCHIVE) fail(PackValidationError.TooLarge,"Pack is too large.") }
    }
    fun decode(bytes: ByteArray): LinkedHashMap<String,ByteArray> {
        if(bytes.size>MAX_ARCHIVE) fail(PackValidationError.TooLarge,"Pack is too large."); val result=linkedMapOf<String,ByteArray>();var p=0;var total=0L
        while(p+4<=bytes.size && bytes.i(p)==0x04034b50){if(p+30>bytes.size) bad();val flags=bytes.s(p+6);val method=bytes.s(p+8);if(flags!=0||method!=0)fail(PackValidationError.UnsupportedEntry,"Encrypted or compressed entries are not supported.");val crc=bytes.i(p+14);val packed=bytes.i(p+18);val size=bytes.i(p+22);val ns=bytes.s(p+26);val es=bytes.s(p+28);val start=p+30+ns+es;if(packed<0||size<0||packed!=size||start<p||start+size>bytes.size)bad();val name=validateName(bytes.decodeToString(p+30,p+30+ns));if(result.containsKey(name))fail(PackValidationError.UnsafePath,"Duplicate pack entry.");total+=size;if(total>MAX_UNPACKED)fail(PackValidationError.TooLarge,"Pack is too large.");val data=bytes.copyOfRange(start,start+size);if(crc32(data)!=crc)fail(PackValidationError.ChecksumMismatch,"Pack checksum failed.");result[name]=data;p=start+size;if(result.size>MAX_ENTRIES)fail(PackValidationError.TooManyEntries,"Pack contains too many entries.")}
        if(result.isEmpty()||p+4>bytes.size||bytes.i(p)!=0x02014b50)bad();return LinkedHashMap(result)
    }
    private fun validateName(raw:String):String{val n=raw.replace('\\','/');if(n.isBlank()||n.startsWith('/')||Regex("^[A-Za-z]:").containsMatchIn(n)||n.split('/').any{it==".."||it.isBlank()}||n.lowercase().endsWith(".zip"))fail(PackValidationError.UnsafePath,"Unsafe pack path.");return n}
    private fun bad():Nothing=fail(PackValidationError.InvalidArchive,"Invalid brush pack archive.")
    private fun fail(e:PackValidationError,m:String):Nothing=throw PackValidationException(e,m)
    private data class Record(val name:String,val size:Int,val crc:Int,val offset:Int)
    private class Bytes{val v=ArrayList<Byte>();val size get()=v.size;fun b(x:Int){v+=x.toByte()};fun s(x:Int){b(x);b(x ushr 8)};fun i(x:Int){s(x);s(x ushr 16)};fun add(a:ByteArray){a.forEach{v+=it}};fun array()=v.toByteArray()}
}
private fun ByteArray.s(p:Int)=(this[p].toInt() and 255) or ((this[p+1].toInt() and 255) shl 8)
private fun ByteArray.i(p:Int)=s(p) or (s(p+2) shl 16)
private fun crc32(data:ByteArray):Int{var c=-1;data.forEach{b->c=c xor(b.toInt() and 255);repeat(8){c=if(c and 1!=0)(c ushr 1) xor 0xedb88320.toInt() else c ushr 1}};return c.inv()}
