package tracks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broad.igv.bbfile.BBFileReader;
import org.broad.igv.bbfile.BigWigIterator;
import org.broad.igv.bbfile.WigItem;
import org.broad.igv.tdf.TDFDataset;
import org.broad.igv.tdf.TDFGroup;
import org.broad.igv.tdf.TDFReader;
import org.broad.igv.tdf.TDFTile;
import org.broad.igv.util.ResourceLocator;

import com.google.common.base.Joiner;

import coloring.Config;
import coloring.ConfigKey;
import coloring.Xterm256;
import exceptions.InvalidColourException;
import exceptions.InvalidGenomicCoordsException;
import exceptions.InvalidRecordException;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.readers.TabixReader;
import htsjdk.tribble.readers.TabixReader.Iterator;
import samTextViewer.GenomicCoords;
import samTextViewer.Utils;
import sortBgzipIndex.MakeTabixIndex;

/** Process wiggle file formats. Mostly using IGV classes. 
 * bigBed, bigWig, */
public class TrackWiggles extends Track {

	// private double scorePerDot;
	private List<ScreenWiggleLocusInfo> screenWiggleLocusInfoList;
	protected int bdgDataColIdx= 4; 
	private BBFileReader bigWigReader;
	
	/* C o n s t r u c t o r s */

	/**
	 * Read bigWig from local file or remote URL.
	 * @param filename Filename or URL to access 
	 * @param gc Query coordinates and size of printable window 
	 * @throws IOException 
	 * @throws InvalidRecordException 
	 * @throws InvalidGenomicCoordsException 
	 * @throws SQLException 
	 * @throws ClassNotFoundException */
	public TrackWiggles(String filename, GenomicCoords gc, int bdgDataColIdx) throws IOException, InvalidRecordException, InvalidGenomicCoordsException, ClassNotFoundException, SQLException{

		this.setFilename(filename);
		this.setWorkFilename(filename);
		this.bdgDataColIdx= bdgDataColIdx;
		this.setTrackFormat(Utils.getFileTypeFromName(this.getWorkFilename()));
		
		if(this.getTrackFormat().equals(TrackFormat.BIGWIG)){
			this.setTrackFormat(TrackFormat.BIGWIG);
			this.bigWigReader=new BBFileReader(this.getWorkFilename()); // or url for remote access.
			if(!this.bigWigReader.getBBFileHeader().isBigWig()){
				throw new RuntimeException("Invalid file type " + this.getWorkFilename());
			}
			
		} else if(this.getTrackFormat().equals(TrackFormat.BEDGRAPH) && ! Utils.hasTabixIndex(filename)){
				String tabixBdg= this.tabixBedgraphToTmpFile(filename);
				this.setWorkFilename(tabixBdg);
		}
		this.setGc(gc);
		
	};
	
	protected TrackWiggles(){
		
	}

	/*  M e t h o d s  */
	@Override
	public void close(){
		if(this.bigWigReader != null){
			this.bigWigReader.close();
		}
	}
	
	@Override
	public void update() throws IOException, InvalidRecordException, InvalidGenomicCoordsException, ClassNotFoundException, SQLException {

		if(this.bdgDataColIdx < 4){
			System.err.println("Invalid index for bedgraph column of data value. Expected >=4. Got " + this.bdgDataColIdx);
			this.bdgDataColIdx= 4;
			throw new InvalidRecordException();
		}

		if(this.getTrackFormat().equals(TrackFormat.BIGWIG)){
			
			this.bigWigToScores(this.bigWigReader);
			
		} else if(this.getTrackFormat().equals(TrackFormat.TDF)){
			
			this.updateTDF();
			
		} else if(this.getTrackFormat().equals(TrackFormat.BEDGRAPH)){

			this.bedGraphToScores(this.getWorkFilename());
			
		} else {
			throw new RuntimeException("Extension (i.e. file type) not recognized for " + this.getWorkFilename());
		}
	}

	protected String tabixBedgraphToTmpFile(String inBdg) throws IOException, ClassNotFoundException, InvalidRecordException, SQLException{
		
		File tmp = Utils.createTempFile(".asciigenome." + new File(inBdg).getName() + ".", ".bedGraph.gz", true);
		File tmpTbi= new File(tmp.getAbsolutePath() + FileExtensions.TABIX_INDEX);
		tmpTbi.deleteOnExit();

		new MakeTabixIndex(inBdg, tmp, TabixFormat.BED);
		return tmp.getAbsolutePath();
		
	}
	
	private void updateTDF() throws InvalidGenomicCoordsException, IOException{
		
		this.screenWiggleLocusInfoList= 
				this.tdfRangeToScreen(this.getWorkFilename(), this.getGc().getChrom(), 
						this.getGc().getFrom(), this.getGc().getTo(), this.getGc().getMapping());
		
		List<Float> screenScores= new ArrayList<Float>();
		for(ScreenWiggleLocusInfo x : screenWiggleLocusInfoList){
			screenScores.add((float)x.getMeanScore());
		}
		if(this.isRpm()){
			screenScores= this.normalizeToRpm(screenScores);
		}
		this.setScreenScores(screenScores);	

	}
	
    /** Fetch data in tdf file in given range and puts it in a list of ScreenWiggleLocusInfo. 
     * a Adapted from dumpRange. Really it should implement iterator.
     * @param genomeToScreenMapping Typically from GenomicCoords.getMapping() 
     * 
     * @author berald01
     * */
    private List<ScreenWiggleLocusInfo> tdfRangeToScreen(String ibfFile, String chrom, int startLocation, int endLocation, List<Double> genomeToScreenMapping) {

        List<ScreenWiggleLocusInfo> screenWiggleLocusInfoList= new ArrayList<ScreenWiggleLocusInfo>();
        for(int i= 0; i < genomeToScreenMapping.size(); i++){
            screenWiggleLocusInfoList.add(new ScreenWiggleLocusInfo());
        }

        TDFReader reader = TDFReader.getReader(ibfFile);

        for (String dsName : reader.getDatasetNames()) {

            String[] tokens = dsName.split("/");
            String chrName = tokens[1];
            if(!chrName.equals(chrom) || !dsName.contains("raw")){ // Not the right chrom or track
                continue;
            }

            TDFDataset ds = reader.getDataset(dsName);

            int tileWidth = ds.getTileWidth();
            int startTile = startLocation / tileWidth;
            int endTile = endLocation / tileWidth;

            for (int tileNumber = startTile; tileNumber <= endTile; tileNumber++) {
                TDFTile tile = reader.readTile(ds, tileNumber);
                if (tile == null) {
                    // System.out.println("Null tile: " + dsName + " [" + tileNumber + "]");
                } else {
                    int nTracks = reader.getTrackNames().length;
                    if(nTracks > 1){
                        throw new RuntimeException("More than one track found in tdf file " + ibfFile);
                    }
                    int nBins = tile.getSize();
                    if (nBins > 0) {
                        for (int b = 0; b < nBins; b++) {
                            int start = tile.getStartPosition(b);
                            int end = tile.getEndPosition(b);
                            if (start > endLocation) {
                                break;
                            }
                            if (end >= startLocation) {
                                int tileStartPos= tile.getStartPosition(b);
                                float tileValue= tile.getValue(0, b);
                                int idx= Utils.getIndexOfclosestValue(tileStartPos+1, genomeToScreenMapping); // Where should this position be mapped on screen?
                                screenWiggleLocusInfoList.get(idx).increment(tileValue);

                            }
                        } // End process bins in this tile
                    }
                } // End process this tile
            } // End iter tiles
        } // End iter datasets names
        reader.close();
        return screenWiggleLocusInfoList;
    }
	
	@Override
	protected void updateToRPM(){
		if(this.getTrackFormat().equals(TrackFormat.TDF)){
			// Re-run update only for track types that can be converted to RPM
			try {
				this.update();
			} catch (ClassNotFoundException | IOException | InvalidRecordException | InvalidGenomicCoordsException | SQLException e) {
				e.printStackTrace();
			}
		}
	}

	
	@Override
	public String printToScreen() throws InvalidColourException{

		if(this.getyMaxLines() == 0){return "";}
		
		TextProfile textProfile= new TextProfile(this.getScreenScores(), this.getyMaxLines(), this.getYLimitMin(), this.getYLimitMax());
		
		ArrayList<String> lineStrings= new ArrayList<String>();
		for(int i= (textProfile.getProfile().size() - 1); i >= 0; i--){
			List<String> xl= textProfile.getProfile().get(i);
			lineStrings.add(StringUtils.join(xl, ""));
		}

		String printable= Joiner.on("\n").join(lineStrings);
		if(!this.isNoFormat()){
			new Xterm256();
			printable= "\033[48;5;"
			+ Config.get256Color(ConfigKey.background)
			+ ";38;5;"
			+ Xterm256.colorNameToXterm256(this.getTitleColour())
			+ "m"
			+ printable;
		}
		return printable;
	}
	
	@Override
	public String getTitle() throws InvalidColourException, InvalidGenomicCoordsException, IOException{

		if(this.isHideTitle()){
			return "";
		}
		
		Float[] range = Utils.range(this.getScreenScores());
		String[] rounded= Utils.roundToSignificantDigits(range[0], range[1], 2);

		String ymin= this.getYLimitMin().isNaN() ? "auto" : this.getYLimitMin().toString();
		String ymax= this.getYLimitMax().isNaN() ? "auto" : this.getYLimitMax().toString();
		
		String xtitle= this.getTrackTag() 
				+ "; ylim[" + ymin + " " + ymax + "]" 
				+ "; range[" + rounded[0] + " " + rounded[1] + "]";
		
		// xtitle= Utils.padEndMultiLine(xtitle, this.getGc().getUserWindowSize());
		return this.formatTitle(xtitle) + "\n";
	}
	
	/** Return true if line looks like a valid bedgraph record  
	 * */
	private boolean isValidBedGraphLine(String line){
		
		if(line.trim().startsWith("#") || line.trim().startsWith("track ")){
			return true;
		}
		
		String[] bdg= line.split("\t");
		if(bdg.length < 4){
			return false;
		}
		try{
			Integer.parseInt(bdg[1]);
			Integer.parseInt(bdg[2]);
			Double.parseDouble(bdg[this.bdgDataColIdx - 1]);
		} catch(NumberFormatException e){
			return false;
		}
		return true;
	}
	
	/** Populate object using bigWig data 
	 * @throws IOException 
	 * @throws InvalidGenomicCoordsException */
	private void bigWigToScores(BBFileReader reader) throws InvalidGenomicCoordsException, IOException{

		// List of length equal to screen size. Each inner map contains info about the screen locus 
		List<ScreenWiggleLocusInfo> screenWigLocInfoList= new ArrayList<ScreenWiggleLocusInfo>();
		for(int i= 0; i < getGc().getUserWindowSize(); i++){
			screenWigLocInfoList.add(new ScreenWiggleLocusInfo());
		}

		BigWigIterator iter = reader.getBigWigIterator(getGc().getChrom(), getGc().getFrom(), getGc().getChrom(), getGc().getTo(), false);
		while(iter.hasNext()){
			WigItem bw = iter.next();
			for(int i= bw.getStartBase(); i <= bw.getEndBase(); i++){
				int idx= Utils.getIndexOfclosestValue(i, this.getGc().getMapping()); // Where should this position be mapped on screen?
				screenWigLocInfoList.get(idx).increment(bw.getWigValue());
			} 
		}
		List<Float> screenScores= new ArrayList<Float>();
		for(ScreenWiggleLocusInfo x : screenWigLocInfoList){
			screenScores.add((float)x.getMeanScore());
		}
		this.setScreenScores(screenScores);		
	}
	
	/** Get values for bedgraph
	 * @throws InvalidRecordException 
	 * @throws InvalidGenomicCoordsException 
	 * */
	protected void bedGraphToScores(String fileName) throws IOException, InvalidRecordException, InvalidGenomicCoordsException{

		List<ScreenWiggleLocusInfo> screenWigLocInfoList= new ArrayList<ScreenWiggleLocusInfo>();
		for(int i= 0; i < getGc().getUserWindowSize(); i++){
			screenWigLocInfoList.add(new ScreenWiggleLocusInfo());
		}

		TabixReader tabixReader= new TabixReader(fileName);
		try {
			Iterator qry= tabixReader.query(this.getGc().getChrom(), this.getGc().getFrom()-1, this.getGc().getTo());
			while(true){
				String q = qry.next();
				if(q == null){
					break;
				}
				if ( !this.isValidBedGraphLine(q) ) {
					continue;
				}
				String[] tokens= q.split("\t");
				int screenFrom= Utils.getIndexOfclosestValue(Integer.valueOf(tokens[1])+1, this.getGc().getMapping());
				int screenTo= Utils.getIndexOfclosestValue(Integer.valueOf(tokens[2]), this.getGc().getMapping());
				float value= Float.valueOf(tokens[this.bdgDataColIdx-1]);
				for(int i= screenFrom; i <= screenTo; i++){
					screenWigLocInfoList.get(i).increment(value);
				}
			}
		} catch (IOException e) {			
			e.printStackTrace();
			System.err.println("Could not open tabix file: " + fileName);
			System.err.println("Is the file sorted and indexed? After sorting by position (sort e.g. -k1,1 -k2,2n), compress with bgzip and index with e.g.:");
			System.err.println("\nbgzip " + fileName);
			System.err.println("tabix -p bed " + fileName + "\n");
		} finally {
			tabixReader.close();
		}

		List<Float> screenScores= new ArrayList<Float>();
		for(ScreenWiggleLocusInfo x : screenWigLocInfoList){
			screenScores.add((float)x.getMeanScore());
		}
		this.setScreenScores(screenScores);
		return;
	}
	
	private List<Float> normalizeToRpm(List<Float> screenScores){
		ArrayList<Float> rpmed= new ArrayList<Float>();
		String x= this.getAttributesFromTDF("totalCount");
		if(x == null){
			System.err.println("Warning: Cannot get total counts for " + this.getFilename());
			return screenScores;
		}
		Integer totalCount= Integer.parseInt(x);
		for(int i= 0; i < screenScores.size(); i++){
			rpmed.add((float) (screenScores.get(i) / totalCount * 1000000.0));
		}
		return rpmed;
	}
	
	private String getAttributesFromTDF(String attr){
		
		String path= this.getWorkFilename();
		
		try{
			ResourceLocator resourceLocator= new ResourceLocator(path);
			TDFReader reader= new TDFReader(resourceLocator);
			TDFGroup rootGroup= reader.getGroup("/");
			return rootGroup.getAttribute(attr);
		} catch(Exception e){
			return null;
		}
	}
	
	@Override
	public List<String> getChromosomeNames(){
		
		if(this.getTrackFormat().equals(TrackFormat.TDF)){

			ResourceLocator resourceLocator= new ResourceLocator(this.getWorkFilename());
			TDFReader reader= new TDFReader(resourceLocator);
			List<String> chroms= new ArrayList<String>(reader.getChromosomeNames());
			if(chroms.get(0).equals("All")){
				chroms.remove(0);
			}
			return chroms;
			// chroms.addAll();
		}
		if(this.getTrackFormat().equals(TrackFormat.BEDGRAPH)){
			TabixIndex tbi= (TabixIndex) IndexFactory.loadIndex(this.getWorkFilename() + FileExtensions.TABIX_INDEX);
			return tbi.getSequenceNames();
		}
		if(this.getTrackFormat().equals(TrackFormat.BIGWIG)){
			return this.bigWigReader.getChromosomeNames();
		}
		return null;
	}
	
	/*   S e t t e r s   and   G e t t e r s */
	
	protected int getBdgDataColIdx() { return bdgDataColIdx; }
	protected void setBdgDataColIdx(int bdgDataColIdx) throws ClassNotFoundException, IOException, InvalidRecordException, InvalidGenomicCoordsException, SQLException { 
		this.bdgDataColIdx = bdgDataColIdx; 
		this.update();
	}

	@Override
	public String printLines(){
		return "";
	}

	@Override
	protected List<String> getRecordsAsStrings() {
		return new ArrayList<String>();
	}

	@Override
	public void setAwk(String awk) throws ClassNotFoundException, IOException, InvalidGenomicCoordsException,
			InvalidRecordException, SQLException {
		//
	}

	@Override
	public String getAwk() {
		return "";
	}

	@Override
	protected String getTitleForActiveFilters() {
		return "";
	}
	
	@Override
	public void reload() throws InvalidGenomicCoordsException, IOException, ClassNotFoundException, InvalidRecordException, SQLException{
		if( ! Files.isSameFile(Paths.get(this.getWorkFilename()), Paths.get(this.getFilename()))){
			TrackWiggles tr= new TrackWiggles(this.getFilename(), this.getGc(), this.getBdgDataColIdx());
			String fname= this.getWorkFilename();
			Files.move(Paths.get(tr.getWorkFilename()), Paths.get(fname), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.move(Paths.get(tr.getWorkFilename() + FileExtensions.TABIX_INDEX), Paths.get(fname + FileExtensions.TABIX_INDEX), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		this.update();
	}

	@Override
	public void setFeatureName(String gtfAttributeForName) {
	}
}
