package com.eyescreening.pterygium;
import java.io.*;
import java.util.zip.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SafeResultArchiveTest {
    private byte[] archive(String[] names,int bytes)throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            for(String name:names){zip.putNextEntry(new ZipEntry(name));zip.write(new byte[bytes]);zip.closeEntry();}
        }
        return output.toByteArray();
    }
    @Test public void expectedFilesReadWithoutExtraction()throws Exception {
        assertEquals(3,SafeResultArchive.read(new ByteArrayInputStream(archive(new String[]{"manifest.json","axial.png","tangential.png"},10))).size());
    }
    @Test(expected=IOException.class) public void traversalPathRejected()throws Exception {
        SafeResultArchive.read(new ByteArrayInputStream(archive(new String[]{"../manifest.json"},10)));
    }
    @Test(expected=IOException.class) public void incompleteRejected()throws Exception {
        SafeResultArchive.read(new ByteArrayInputStream(archive(new String[]{"manifest.json"},10)));
    }
    @Test(expected=IOException.class) public void inflatedMetadataRejected()throws Exception {
        SafeResultArchive.read(new ByteArrayInputStream(archive(new String[]{"manifest.json"},65537)));
    }
    private static final String HASH=new String(new char[64]).replace('\0','a');
    @Test public void matchingCaptureAccepted()throws Exception {
        SafeResultArchive.validateIdentity("eye-screening-result/1","measured-research",HASH,"capture",HASH,"capture");
    }
    @Test(expected=IOException.class) public void wrongSourceRejected()throws Exception {
        SafeResultArchive.validateIdentity("eye-screening-result/1","measured-research",HASH,"capture",HASH.replace('a','b'),"capture");
    }
    @Test(expected=IOException.class) public void wrongCaptureRejected()throws Exception {
        SafeResultArchive.validateIdentity("eye-screening-result/1","measured-research",HASH,"old-capture",HASH,"new-capture");
    }
    @Test(expected=IOException.class) public void referenceCannotBecomeLiveResult()throws Exception {
        SafeResultArchive.validateIdentity("eye-screening-result/1","reference-only",HASH,"capture",HASH,"capture");
    }
    @Test public void simulationIdentityRequiresExplicitSeparateMode()throws Exception {
        SafeResultArchive.validateSimulationIdentity("eye-screening-result/1","assumed-study-geometry-demo",HASH,"capture",HASH,"capture");
    }
    @Test(expected=IOException.class) public void referenceCannotMasqueradeAsSimulation()throws Exception {
        SafeResultArchive.validateSimulationIdentity("eye-screening-result/1","reference-only",HASH,"capture",HASH,"capture");
    }
    @Test(expected=IOException.class) public void simulationCannotBecomeMeasuredResult()throws Exception {
        SafeResultArchive.validateIdentity("eye-screening-result/1","assumed-study-geometry-demo",HASH,"capture",HASH,"capture");
    }
    @Test(expected=IOException.class) public void wrongSourceSimulationRejected()throws Exception {
        SafeResultArchive.validateSimulationIdentity("eye-screening-result/1","assumed-study-geometry-demo",HASH,"capture",HASH.replace('a','b'),"capture");
    }
}
