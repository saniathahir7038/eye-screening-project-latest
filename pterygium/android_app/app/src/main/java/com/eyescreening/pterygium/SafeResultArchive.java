package com.eyescreening.pterygium;
import java.io.*;
import java.util.*;
import java.util.zip.*;
/** Read only the three expected files; never extract archive paths to disk. */
final class SafeResultArchive {
    static void validateIdentity(String schema,String mode,String sourceHash,String captureId,
            String expectedHash,String expectedId) throws IOException {
        if(!"eye-screening-result/1".equals(schema)||!"measured-research".equals(mode))
            throw new IOException("Choose a measured capture result; reference results belong in sample mode");
        if(expectedHash==null||expectedId==null||!expectedHash.matches("[0-9a-f]{64}")||expectedId.isEmpty()
                ||!expectedHash.equals(sourceHash)||!expectedId.equals(captureId))
            throw new IOException("These maps belong to a different capture. Export this photo and open its reconstructed result ZIP");
    }
    static void validateSimulationIdentity(String schema,String mode,String sourceHash,String captureId,
            String expectedHash,String expectedId) throws IOException {
        if(!"assumed-study-geometry-demo".equals(mode))throw new IOException("Unsupported simulation mode");
        validateIdentity(schema,"measured-research",sourceHash,captureId,expectedHash,expectedId);
    }
    static Map<String,byte[]> read(InputStream input) throws IOException {
        Map<String,byte[]> files=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(input)) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName();
                if(!name.equals("manifest.json") && !name.equals("axial.png") && !name.equals("tangential.png"))
                    throw new IOException("Unexpected result file: "+name);
                if(files.containsKey(name)) throw new IOException("Duplicate result file");
                int limit=name.equals("manifest.json")?65536:8*1024*1024;
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                byte[] buffer=new byte[8192];int n;
                while((n=zip.read(buffer))!=-1) {
                    if(output.size()+n>limit) throw new IOException("Result file exceeds size limit");
                    output.write(buffer,0,n);
                }
                files.put(name,output.toByteArray());
            }
        }
        if(files.size()!=3) throw new IOException("Incomplete map package");
        return files;
    }
}
