package psi;

import java.nio.ByteBuffer;

/**
 *
 * @author krol01
 */
public class PSIUtil {

    private final static byte PAT_TABLE_ID = (byte) 0x00;
    private final static byte PMT_TABLE_ID = (byte) 0x02;
    private static byte[] PAT_PID_BYTES = {(byte) 0x00, (byte) 0x00};  //0
    private static byte[] PMT_PID_BYTES = {(byte) 0x10, (byte) 0x00};  //4096
    public static int PAT_PID = 0;
    public static int PMT_PID = 4096;

    //PSI
    //Pointer field  8 bits always zero for PAT and PMT
    //Pointer filler bytes	N*8	When the pointer field is non-zero, this is the pointer field number of
    //alignment padding bytes set to 0xFF or the end of the previous table
    //section spanning across TS packets (electronic program guide).
    //Table ID 8 bits
    //Section syntax indicator  1 bit  (1 for PAT PMT)
    //Private bit  1 bit (0 for PAT, PMT)
    //Reserved bits 2 bits  (all set to 1)
    //Section length unused bits  2 bits  (all set to 0)
    //Section length 10 bits (The number of bytes that follow for the
    //                        syntax section (with CRC value) and/or table data.
    //                        These bytes must not exceed a value of 1021.)
    //Syntax section/Table data N*8 bits  (When the section length is non-zero,
    //                                     this is the section length number of syntax
    //                                     and data bytes. )
    //TABLE SYNTAX SECTION
    //Table ID Extension  16 bits   (PAT uses ts identifier (1?), PMT uses program number (1?))
    //Reserved bits 2 bits (all set to 1)
    //Version number 5 bits (0?)
    //Current/next indicator 1 bit (1 means valid now)
    //Section number  //8 bits  0
    //Last section number // 8 bits  0
    //Table data N*8  (PAT OR PMT)
    //CRC32 32 bits A checksum of the entire table excluding the pointer field, pointer filler bytes and the trailing CRC32.

    //PAT
    //Program num 16 bits (Relates to the Table ID extension in the associated PMT 1 (0x0001))
    //Reserved bits 3 bits (all is set to 1)
    //Program map PID 13 bits (The packet identifier that contains the associated PMT (4096 (0x1000)

    /*
    //PMT
    //Reserved bits 3 bits (all set to 1)
    //PCR PID 13 bits (The packet identifier that contains the program clock reference used to improve
                        //the random access accuracy of the stream's timing that is derived from the program
    //timestamp. If this is unused. then it is set to 0x1FFF (all bits on).
    //256 (0x0100)
    //Reserved bits 4 bits Set to 0x0F (all bits on)
    //Program info length unused bits 2 bits (set to 0)
    //Program info length 10 bits (The number of bytes that follow for the program descriptors.) 0 (0x0000)
    //Program descriptors N*8 bits (irrelevant if program info length is 0)
    //Elementary stream info data N*8 (The streams used in this program map.)
        //FOR ALL STREAMS {
        //stream type                   8 bits ( 27 (0x1b)  [= AVC video stream as defined in ITU-T Rec. H.264 | ISO/IEC 14496-10 Video])
        //15 (0x0f)  [= ISO/IEC 13818-7 Audio with ADTS transport sytax]
        //Reserved bits                 3 bits	Set to 0x07 (all bits on)
        //Elementary PID                13 bits The packet identifier that contains the stream type data.
        //Reserved bits                 4 bits	Set to 0x0F (all bits on)
        //ES Info length unused bits    2 bits Set to 0 (all bits off)
        //ES Info length length         10 bits (0) The number of bytes that follow for the elementary stream descriptors.
        //Elementary stream descriptors N*8	When the ES info length is non-zero, this is the ES info length number of elementary stream descriptor bytes
        //}
    */
    public static ByteBuffer getPAT() {
        ByteBuffer patBuf = ByteBuffer.allocate(4);
        patBuf.put((byte) 0x00); //program number byte 0
        patBuf.put((byte) 0x01); //program number byte 1

        patBuf.put((byte) (PMT_PID_BYTES[0] | 0xE0));
        patBuf.put((PMT_PID_BYTES[1]));
        patBuf.flip();
        return addSyntaxSection(patBuf, PAT_TABLE_ID);
    }

    public static ByteBuffer getPMT(int pid, int streamType) {
        //SIMPLIFICATION 40 bit PMT (Only one stream)
        ByteBuffer pmtBuf = ByteBuffer.allocate(9);
        byte b0 = (byte) (((pid >> 8) & 0xFF) | 0xE0);
        byte b1 = (byte) (pid & 0xFF);

        pmtBuf.put(b0);
        pmtBuf.put(b1);
        pmtBuf.put((byte) 0xF0);
        pmtBuf.put((byte) 0x00);
        //Elementary stream (only one)
        pmtBuf.put((byte) streamType);
        pmtBuf.put(b0);
        pmtBuf.put(b1);
        pmtBuf.put((byte) 0xF0);
        pmtBuf.put((byte) 0x00);

        pmtBuf.flip();
        return addSyntaxSection(pmtBuf, PMT_TABLE_ID);
    }

    private static ByteBuffer addPSI(ByteBuffer syntaxSection, byte tableId) {
        ByteBuffer psiBuf = ByteBuffer.allocate(4 + syntaxSection.capacity() + 4);  //4 bytes additional PSI and 4 bytes CRC
        psiBuf.put((byte)0);  //pointer
        psiBuf.put(tableId);
        //101100XX XXXXXXXX  (where X is Section length)  The number of bytes that follow for the  syntax section (with CRC value) and/or table data
        int length = syntaxSection.capacity() + 4; //including crc32
        psiBuf.put((byte) (((length >> 8) & 0x0F) | 0xB0));
        psiBuf.put((byte) (length & 0xFF));

        psiBuf.put(syntaxSection);
        int crcPos = psiBuf.position();
        psiBuf.position(1);  //Skip pointer field when calculating CRC32
        psiBuf.limit(crcPos);
        //Calc CRC32 and put 32 bits last
        int crc = Crc32.compute(psiBuf);
        psiBuf.limit(psiBuf.capacity());
        psiBuf.position(crcPos);
        psiBuf.putInt(crc);

        psiBuf.flip();
        return psiBuf;
    }

    private static ByteBuffer addSyntaxSection(ByteBuffer tableData, byte tableId) {  //PMT or PAT
        ByteBuffer tableSyntaxSectionBuf = ByteBuffer.allocate(5 + tableData.capacity());  //fix this
        tableSyntaxSectionBuf.put((byte) 0x00);
        tableSyntaxSectionBuf.put((byte) 0x01);
        tableSyntaxSectionBuf.put((byte) 0xC1); //hardcoded reserved, version, current/next indicator
        tableSyntaxSectionBuf.put((byte) 0x00); //section number
        tableSyntaxSectionBuf.put((byte) 0x00); //Last section number
        tableSyntaxSectionBuf.put(tableData);
        tableSyntaxSectionBuf.flip();
        return addPSI(tableSyntaxSectionBuf, tableId);
    }

}
