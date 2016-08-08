package com.ashish_jindal.climate_data;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;

import com.opencsv.CSVReader;

import ucar.ma2.Array;
import ucar.ma2.ArrayDouble;
import ucar.ma2.DataType;
import ucar.ma2.Index;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;

/**
 * Hello world!
 *
 */
public class App {
	public static void main(String[] args) throws IOException, InvalidRangeException {
		
		if (args.length > 0) {
			ClimateData data = new ClimateData();
			File node = new File(args[0]);
			String path = "";
			if (node.isDirectory()) {
				path = node.getPath() + "/";
				String[] files = node.list();
				for(String filename : files){
					CSVReader reader = null;
					try {
						reader = new CSVReader(new FileReader(node.getPath() + "/" + filename));
						String[] line;
						while ((line = reader.readNext()) != null) {
							data.addRow(new Row(line));
						}
						
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} else {

				String csvFile = args[0];
				path = csvFile.substring(0, csvFile.lastIndexOf('/') + 1);

				CSVReader reader = null;
				try {
					reader = new CSVReader(new FileReader(csvFile));
					String[] line;
					while ((line = reader.readNext()) != null) {
						data.addRow(new Row(line));
					}
					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			data.cleanData();
			data.printStats();

			String ncdfFileName = path + "output.nc";
			prepareNCDFFile(data, ncdfFileName);
		} else {
			System.out.println("Please provide the csv file path.");
		}
	}
	
	public static void prepareNCDFFile(ClimateData data, String filename) throws IOException, InvalidRangeException {
		NetcdfFileWriter writer = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, filename, null);

		int[] validTimes = data.getValidTimes();
		int[] countyFips = data.getAllCounties();

		Integer countCounties = countyFips.length;
		Integer countTimes = validTimes.length;
		Dimension dimCounty = writer.addDimension(null, "county", countCounties);
		Dimension dimTime = writer.addDimension(null, "time", countTimes);

		Variable varTime = writer.addVariable(null, "time", DataType.INT, "time");
		Variable varCounty = writer.addVariable(null, "county", DataType.INT, "county");
		Variable tas = writer.addVariable(null, "tas", DataType.DOUBLE, "time county");
		
		// create the file
		try {
			writer.create();
			writer.write(varTime, Array.factory(validTimes));
			writer.write(varCounty, Array.factory(countyFips));
			
			ArrayDouble.D2 tempData = new ArrayDouble.D2(dimTime.getLength(), dimCounty.getLength());
			Index idx = tempData.getIndex();
			int countyIdx = 0;
			for (Integer fipsCode : countyFips) {
				int[] timeData = data.getTimeData(fipsCode);
				Arrays.sort(timeData);
				int timeIdx = 0;
				for (int time : validTimes) {
					tempData.setDouble(idx.set(timeIdx, countyIdx), data.findData(fipsCode, time).temp);
					timeIdx++;
				}
				
				countyIdx++;
			}

		    int[] origin = new int[]{0, 0};
		    writer.write(tas, origin, tempData);
		} catch (IOException e) {
			System.err.printf("ERROR creating file %s%n%s", filename, e.getMessage());
		}
		writer.close();
	}
	
	static class Row {
		public int time;
		public int fips;
		public String state;
		public String countyName;
		public double lon;
		public double lat;
		public double temp;
		
		public boolean isValid;
		public Row(String[] line) {
			try {
				time = Integer.parseInt(line[1].trim());
				fips = Integer.parseInt(line[2].trim());
				state = line[3];
				countyName = line[4];
				lon = Double.parseDouble(line[5].trim());
				lat = Double.parseDouble(line[6].trim());
				temp = Double.parseDouble(line[7].trim());
				isValid = true;
			} catch (Exception e) {
				isValid = false;
			}
		}
	}
	
	static class ClimateData {
		// FIPS code vs corresponding list of rows
		private Map<Integer, HashMap<Integer, Row>> data = new HashMap<Integer, HashMap<Integer, Row>>();

		// Time vs List of counties
		private Map<Integer, HashSet<Integer>> timeVsCounty = new HashMap<Integer, HashSet<Integer>>();

		public int cleanedRows = 0;
		
		public void addRow(Row r) {
			if (r.isValid) {
				if (data.get(r.fips) == null) {
					data.put(r.fips, new HashMap<Integer, Row>());
				}
				
				data.get(r.fips).put(r.time, r);

				if (timeVsCounty.get(r.time) == null) {
					timeVsCounty.put(r.time, new HashSet<Integer>());
				}
				
				timeVsCounty.get(r.time).add(r.fips);
			}
		}
		
		public int[] getTimeData(Integer fips) {
			List<Integer> timeData = new ArrayList<Integer>();
			List<Row> d = getCountyData(fips);
			for (Row r : d) {
				timeData.add(r.time);
			}
			
			Integer[] temp = new Integer[timeData.size()];
			temp = timeData.toArray(temp);
			
			int[] res = ArrayUtils.toPrimitive(temp);
			return res;
		}

		public int[] getFipsData(Integer fips) {
			List<Integer> res = new ArrayList<Integer>();
			List<Row> d = getCountyData(fips);
			for (Row r : d) {
				res.add(r.fips);
			}

			Integer[] temp = new Integer[res.size()];
			temp = res.toArray(temp);
			
			int[] retVal = ArrayUtils.toPrimitive(temp);
			return retVal;
		}

		public List<String> getStateData(Integer fips) {
			List<String> stateData = new ArrayList<String>();
			List<Row> d = getCountyData(fips);
			for (Row r : d) {
				stateData.add(r.state);
			}

			return stateData;
		}

		public List<String> getCountyNameData(Integer fips) {
			List<String> countyData = new ArrayList<String>();
			List<Row> d = getCountyData(fips);
			for (Row r : d) {
				countyData.add(r.countyName);
			}

			return countyData;
		}

		public double[] getLonData(Integer fips) {
			List<Double> res = new ArrayList<Double>();
			List<Row> d = getCountyData(fips);
			for (Row r : d) {
				res.add(r.lon);
			}

			Double[] temp = new Double[res.size()];
			temp = res.toArray(temp);
			
			double[] retVal = ArrayUtils.toPrimitive(temp);
			
			return retVal;
		}

		public double[] getLatData(Integer fips) {
			List<Double> res = new ArrayList<Double>();
			List<Row> d = getCountyData(fips);
			for (Row r : d) {
				res.add(r.lat);
			}

			Double[] temp = new Double[res.size()];
			temp = res.toArray(temp);
			
			double[] retVal = ArrayUtils.toPrimitive(temp);
			
			return retVal;
		}

		public double[] getTempData(Integer fips) {
			List<Double> res = new ArrayList<Double>();
			List<Row> d = getCountyData(fips);
			for (Row r : d) {
				res.add(r.temp);
			}

			Double[] temp = new Double[res.size()];
			temp = res.toArray(temp);
			
			double[] retVal = ArrayUtils.toPrimitive(temp);
			
			return retVal;
		}
		
		public void printStats() {
			System.out.println("Number of Counties = " + data.keySet().size());
			for(Integer fips : data.keySet()) {
				System.out.println("Number of rows for county " + fips + " = " + data.get(fips).values().size());
			}
		}
		
		public int[] getValidTimes() {
			Set<Integer> res = timeVsCounty.keySet();
			
			Integer[] temp = new Integer[res.size()];
			temp = res.toArray(temp);
			
			int[] retVal = ArrayUtils.toPrimitive(temp);
			
			return retVal;
		}
		
		public List<Row> getCountyData(int fips) {
			return new ArrayList<Row>(data.get(fips).values());
		}
		
		public int[] getAllCounties() {
			Set<Integer> res = data.keySet();
			
			Integer[] temp = new Integer[res.size()];
			temp = res.toArray(temp);
			
			int[] retVal = ArrayUtils.toPrimitive(temp);
			
			return retVal;
		}
		
		public Row findData(int fips, int time) {
			HashMap<Integer, Row> d = data.get(fips);
			return d.get(time);
		}
		
		public boolean isTimeCountyPairExists(Integer time, Integer fips) {
			return (timeVsCounty.get(time) != null && timeVsCounty.get(time).contains(fips));
		}
		
		public void cleanData() {
			for(Integer fips : data.keySet()) {
				System.out.println("No of time stamps for county " + fips + " = " + data.get(fips).keySet().size());
				Iterator<Integer> it = timeVsCounty.keySet().iterator();
				while(it.hasNext()) {
					int time = it.next();
					if (!isTimeCountyPairExists(time, fips)) {
						removeRowForAll(time);
						it.remove();
					} 
				}
			}
			
			System.out.println("Number of rows removed = " + cleanedRows);
		}
		
		public void removeRowForAll(Integer time) {
			for(Integer fips : data.keySet()) {
				data.get(fips).remove(time);
			}
			
			cleanedRows++;
		}
	}
}
