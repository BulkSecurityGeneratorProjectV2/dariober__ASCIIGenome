package tracks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import org.broad.igv.bbfile.BBFileReader;
import org.broad.igv.bbfile.BigWigIterator;
import org.junit.Before;
import org.junit.Test;

import coloring.Config;
import exceptions.InvalidColourException;
import exceptions.InvalidConfigException;
import exceptions.InvalidGenomicCoordsException;
import exceptions.InvalidRecordException;
import samTextViewer.GenomicCoords;

public class TrackWigglesTest {

	@Before
	public void prepareConfig() throws IOException, InvalidConfigException{
		new Config(null);
	}

	@Test
	public void canCloseReaders() throws ClassNotFoundException, IOException, InvalidRecordException, InvalidGenomicCoordsException, SQLException{
		GenomicCoords gc= new GenomicCoords("chr7:5540000-5570000", 80, null, null);
		TrackWiggles tw= new TrackWiggles("test_data/hg18_var_sample.wig.v2.1.30.tdf", gc, 4);
		tw.close();
		
		tw= new TrackWiggles("test_data/test.bedGraph", gc, 4);
		tw.close();
		
		//tw= new TrackWiggles("http://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeHaibTfbs/wgEncodeHaibTfbsA549Cebpbsc150V0422111RawRep1.bigWig", gc, 4);
		//tw.close();
	}
	
	@Test
	public void canPrintChromosomeNames() throws InvalidGenomicCoordsException, IOException, ClassNotFoundException, InvalidRecordException, SQLException{

		GenomicCoords gc= new GenomicCoords("chr7:5540000-5570000", 80, null, null);
		TrackWiggles tw= new TrackWiggles("test_data/hg18_var_sample.wig.v2.1.30.tdf", gc, 4);
		assertTrue(tw.getChromosomeNames().size() > 10);
		
		tw= new TrackWiggles("test_data/test.bedGraph", gc, 4);
		assertTrue(tw.getChromosomeNames().size() > 0);
		
		//tw= new TrackWiggles("http://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeHaibTfbs/wgEncodeHaibTfbsA549Cebpbsc150V0422111RawRep1.bigWig", gc, 4);
		//assertTrue(tw.getChromosomeNames().size() > 10);
	}
	
	// @Test
	// public void canReadBigWigFromRemote() throws IOException{
	// 	// String urlStr= "http://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeHaibTfbs/wgEncodeHaibTfbsA549Atf3V0422111Etoh02RawRep1.bigWig";
	// 	String urlStr= "http://hgdownload.cse.ucsc.edu/goldenPath/hg19/encodeDCC/wgEncodeHaibTfbs/wgEncodeHaibTfbsA549Cebpbsc150V0422111RawRep1.bigWig";
	// 	BBFileReader reader=new BBFileReader(urlStr);
	// 	System.out.println(reader.getChromosomeNames());
	// 	BigWigIterator iter = reader.getBigWigIterator("chr1", 1000000, "chr1", 2000000, true);
	// 	while(iter.hasNext()){
	// 		System.out.println(iter.next().getStartBase());
	// 	}
	// 	System.out.println("NEW");
	// 	iter = reader.getBigWigIterator("chr10", 1000000, "chr10", 2000000, true);
	// 		while(iter.hasNext()){
	// 			System.out.println(iter.next().getStartBase());
	// 		}
	// 	reader.close();
	// }
	
	@Test
	public void canGetDataColumnIndexForBedGraph() throws IOException, NoSuchAlgorithmException, InvalidGenomicCoordsException, InvalidRecordException, ClassNotFoundException, SQLException{
		String url= "test_data/test.bedGraph";
		GenomicCoords gc= new GenomicCoords("chr1:1-30", 80, null, null);
		TrackWiggles tw= new TrackWiggles(url, gc, 5);
		assertEquals(0, tw.getScreenScores().get(0), 0.0001);
	}

	@Test
	public void invalidBedgraphRecordAsNaN() throws IOException, NoSuchAlgorithmException, InvalidGenomicCoordsException, InvalidRecordException, ClassNotFoundException, SQLException{
		
		GenomicCoords gc= new GenomicCoords("chr1:1-10", 10, null, null);
		TrackWiggles tw= new TrackWiggles("test_data/invalid-1.bedgraph", gc, 4);
		assertEquals(tw.getScreenScores().get(0), Float.NaN, 0.0001);
		assertEquals(tw.getScreenScores().get(1), 10, 0.0001);
		
	}
		
	@Test
	public void canParseNonBGZFFile() throws IOException, InvalidGenomicCoordsException, InvalidRecordException, ClassNotFoundException, SQLException{
		
		String url= "test_data/test2.bedGraph";
		GenomicCoords gc= new GenomicCoords("chr1:1-30", 80, null, null);
		new TrackWiggles(url, gc, 4);
				
	}
	
	@Test
	public void testYLimits() throws InvalidGenomicCoordsException, IOException, InvalidColourException, InvalidRecordException, ClassNotFoundException, SQLException{

		// String url= "tmp.bedGraph";
		// GenomicCoords gc= new GenomicCoords("chr1:4389687-26338119", 80, null, null);
		String url= "test_data/test.bedGraph.gz";
		GenomicCoords gc= new GenomicCoords("chr1:1-30", 80, null, null);
				
		TrackWiggles tw= new TrackWiggles(url, gc, 4);
		tw.setYLimitMax((float)10.0);
		tw.setYLimitMin((float)-10.0);
		tw.setyMaxLines(10);
		String prof= tw.printToScreen();
		assertTrue(prof.contains(","));
		assertTrue(prof.contains(":"));
	}
	
	@Test
	public void testCloseToBorder() throws InvalidGenomicCoordsException, InvalidColourException, IOException, InvalidRecordException, ClassNotFoundException, SQLException{
		String url= "test_data/test.bedGraph.gz";
		int yMaxLines= 10;
		GenomicCoords gc= new GenomicCoords("chr1:1-800", 80, null, null);
		TrackWiggles tw= new TrackWiggles(url, gc, 4);
		tw.setYLimitMax(Float.NaN);
		tw.setYLimitMin(Float.NaN);
		tw.setyMaxLines(yMaxLines);
		String prof= tw.printToScreen();
		System.out.println(prof);
	} 
	
	
	@Test 
	public void canPrintBedGraph() throws InvalidGenomicCoordsException, IOException, InvalidRecordException, ClassNotFoundException, SQLException, InvalidColourException, InvalidConfigException{
		
		new Config(null);
		
		String url= "test_data/test.bedGraph.gz";
		int yMaxLines= 5;
		GenomicCoords gc= new GenomicCoords("chr1:1-22", 80, null, null);
		TrackWiggles tw= new TrackWiggles(url, gc, 4);
		tw.setYLimitMax(Float.NaN);
		tw.setYLimitMin(Float.NaN);
		tw.setyMaxLines(yMaxLines);
		String prof= tw.printToScreen();
		System.out.println(prof);
		
		tw= new TrackWiggles("test_data/positive.bedGraph.gz", gc, 4);
		tw.setYLimitMax(Float.NaN);
		tw.setYLimitMin(Float.NaN);
		tw.setyMaxLines(5);
		prof= tw.printToScreen();
		System.out.println(prof);
		
		tw= new TrackWiggles("test_data/negative.bedGraph.gz", gc, 4);
		tw.setYLimitMax(Float.NaN);
		tw.setYLimitMin(Float.NaN);
		tw.setyMaxLines(5);
		// prof= tw.printToScreen();

		System.out.println(prof);
		
		gc= new GenomicCoords("chr1:1-52", 80, null, null);
		tw= new TrackWiggles("test_data/posNeg.bedGraph.gz", gc, 4);
		tw.setYLimitMax(Float.NaN);
		tw.setYLimitMin(Float.NaN);
		tw.setyMaxLines(14);
		System.out.println(tw.printToScreen());
	}
	
	@Test
	/** Snippet to extract totalCount from TDF, useful for normalizing signal. 
	 * */
	public void canNomrmalizeTDFtoRPM() throws InvalidGenomicCoordsException, IOException, InvalidRecordException, ClassNotFoundException, SQLException{

		System.out.println("START");
		GenomicCoords gc= new GenomicCoords("chr7:5540000-5570000", 80, null, null);
		TrackWiggles tw= new TrackWiggles("test_data/ear045.oxBS.actb.tdf", gc, 4);
		Float raw= tw.getScreenScores().get(0);
		tw.setRpm(true);
		Float rpm= tw.getScreenScores().get(0);
		assertTrue(rpm > raw);
	}
		
}
