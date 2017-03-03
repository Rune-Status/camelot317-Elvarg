package com.runescape.net.requester;
import java.io.*;
import java.net.Socket;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

import com.runescape.Client;
import com.runescape.Configuration;
import com.runescape.cache.FileArchive;
import com.runescape.collection.Deque;
import com.runescape.collection.Queue;
import com.runescape.io.Buffer;
import com.runescape.sign.SignLink;

import jdk.nashorn.internal.runtime.regexp.joni.Config;
import sun.security.krb5.internal.crypto.crc32;

public final class ResourceProvider extends Provider implements Runnable {

	private int totalFiles;
	private final Deque requested;
	private int maximumPriority;
	public String loadingMessage;
	private int deadTime;
	private long lastRequestTime;
	private int[] landscapes;
	private final byte[] payload;
	public int tick;
	private final byte[][] fileStatus;
	private Client clientInstance;
	private final Deque extras;
	private int completedSize;
	private int remainingData;
	private int[] musicPriorities;
	public int errors;
	private int[] mapFiles;
	private int filesLoaded;
	private boolean running;
	private OutputStream outputStream;
	private int[] membersArea;
	private boolean expectingData;
	private final Deque complete;
	private final byte[] gzipInputBuffer;
	private int[] anIntArray1360;
	private final Queue requests;
	private InputStream inputStream;
	private Socket socket;
	private final int[][] versions;
	private int uncompletedCount;
	private int completedCount;
	private final Deque unrequested;
	private Resource current;
	private final Deque mandatoryRequests;
	private int[] areas;
	private byte[] modelIndices;
	private int idleTime;
	private final CRC32 crc32;

	public ResourceProvider() {
		requested = new Deque();
		loadingMessage = "";
		payload = new byte[100];
		fileStatus = new byte[4][];
		extras = new Deque();
		running = true;
		expectingData = false;
		complete = new Deque();
		gzipInputBuffer = new byte[0x71868];
		requests = new Queue();
		versions = new int[4][];
		unrequested = new Deque();
		mandatoryRequests = new Deque();
		crc32 = new CRC32();
	}

	private void respond() {
		try {
			int available = inputStream.available();
			if (remainingData == 0 && available >= 10) {
				expectingData = true;
				for (int skip = 0; skip < 10; skip += inputStream.read(payload, skip, 10 - skip))
					;
				int type = payload[0] & 0xff;
				int file = ((payload[1] & 0xff) << 16) + ((payload[2] & 0xff) << 8) + (payload[3] & 0xff);
				int length = ((payload[4] & 0xff) << 32) + ((payload[5] & 0xff) << 16) + ((payload[6] & 0xff) << 8) + (payload[7] & 0xff);
				int sector = ((payload[8] & 0xff) << 8) + (payload[9] & 0xff);
				current = null;
				for (Resource resource = (Resource) requested.reverseGetFirst(); resource != null; resource = (Resource) requested.reverseGetNext()) {
					if (resource.dataType == type && resource.ID == file)
						current = resource;
					if (current != null)
						resource.loopCycle = 0;
				}

				if (current != null) {
					idleTime = 0;
					if (length == 0) {
						SignLink.reporterror("Rej: " + type + "," + file);
						current.buffer = null;
						if (current.incomplete)
							synchronized (complete) {
								complete.insertHead(current);
							} else {
								current.unlink();
							}
						current = null;
					} else {
						if (current.buffer == null && sector == 0)
							current.buffer = new byte[length];
						if (current.buffer == null && sector != 0)
							throw new IOException("missing start of file");
					}
				}
				completedSize = sector * 500;
				remainingData = 500;
				if (remainingData > length - sector * 500)
					remainingData = length - sector * 500;
			}
			if (remainingData > 0 && available >= remainingData) {
				expectingData = true;
				byte data[] = payload;
				int read = 0;
				if (current != null) {
					data = current.buffer;
					read = completedSize;
				}
				for (int skip = 0; skip < remainingData; skip += inputStream.read(data, skip + read, remainingData - skip));
				if (remainingData + completedSize >= data.length && current != null) {
					//if (clientInstance.indices[0] != null)
					clientInstance.indices[current.dataType + 1].writeFile(data.length, data, current.ID);
					if (!current.incomplete && current.dataType == 3) {
						current.incomplete = true;
						current.dataType = 93;
					}
					if (current.incomplete)
						synchronized (complete) {
							complete.insertHead(current);
						} else {
							current.unlink();
						}
				}
				remainingData = 0;
			}
		} catch (IOException ex) {
			try {
				socket.close();
			} catch (Exception _ex) {
				_ex.printStackTrace();
			}
			socket = null;
			inputStream = null;
			outputStream = null;
			remainingData = 0;
		}
	}

	public int mapAmount = 0;

	private final String crcNames[] = {"model_crc", "anim_crc", "midi_crc", "map_crc"};
	private final int[][] crcs = new int[crcNames.length][];

	public void initialize(FileArchive archive, Client client) {

		for(int i = 0; i < crcNames.length; i++) {
			byte[] crc_file = archive.readFile(crcNames[i]);
			int length = 0;

			if(crc_file != null) {
				length = crc_file.length / 4;
				Buffer crcStream = new Buffer(crc_file);
				crcs[i] = new int[length];
				fileStatus[i] = new byte[length];
				for(int ptr = 0; ptr < length; ptr++) {
					crcs[i][ptr] = crcStream.readInt();
				}
			} 
		}



		byte[] mapData = archive.readFile("map_index");
		Buffer stream2 = new Buffer(mapData);
		int j1 = stream2.readUShort();//mapData.length / 6;
		areas = new int[j1];
		mapFiles = new int[j1];
		landscapes = new int[j1];
		for (int i2 = 0; i2 < j1; i2++) {
			areas[i2] = stream2.readUShort();
			mapFiles[i2] = stream2.readUShort();
			landscapes[i2] = stream2.readUShort();
			mapAmount++;
		}
		System.out.println("Map Amount: " + mapAmount + "");

		mapData = archive.readFile("midi_index");
		stream2 = new Buffer(mapData);
		j1 = mapData.length;
		musicPriorities = new int[j1];
		for (int k2 = 0; k2 < j1; k2++)
			musicPriorities[k2] = stream2.readUnsignedByte();

		clientInstance = client;
		running = true;
		clientInstance.startRunnable(this, 2);
	}

	public int remaining() {
		synchronized (requests) {
			return requests.size();
		}
	}

	public void disable() {
		running = false;
	}

	public void preloadMaps(boolean members) {
		for (int area = 0; area < areas.length; area++) {
			if (members || membersArea[area] != 0) {
				requestExtra((byte) 2, 3, landscapes[area]);
				requestExtra((byte) 2, 3, mapFiles[area]);
			}
		}
	}

	public int getVersionCount(int index) {
		return versions[index].length;
	}

	private void request(Resource resource) {
		try {

			if (socket == null || !socket.isConnected()) {
				long currentTime = System.currentTimeMillis();
				if (currentTime - lastRequestTime < 4000L) {
					System.out.println("Dont open");
					return;
				}
				lastRequestTime = currentTime;
				socket = clientInstance.openSocket(Configuration.FILE_SERVER_PORT);
				inputStream = socket.getInputStream();
				outputStream = socket.getOutputStream();
				outputStream.write(15);
				for (int index = 0; index < 8; index++)
					inputStream.read();

				idleTime = 0;
			}


			//Send data type
			payload[0] = (byte) resource.dataType;

			//Send file id as int
			payload[1] = (byte) (resource.ID >> 24);
			payload[2] = (byte) (resource.ID >> 16);
			payload[3] = (byte) (resource.ID >> 8);
			payload[4] = (byte) resource.ID;

			//Send priority
			/*if (resource.incomplete)
				payload[5] = 2;
			else if (!Client.loggedIn)
				payload[5] = 1;
			else
				payload[5] = 0;*/


			//Priority 1 = HIGH, 2 = MEDIUM , 3 = LOW
			if(!resource.incomplete)
				payload[5] = 1; //HIGH PRIORITY
			else 
				payload[5] = 2; //MEDIUM PRIORITY
			if (!Client.loggedIn)
				payload[5] = 3; //LOW PRIORITY

			//Write the buffer
			outputStream.write(payload, 0, 6);

			deadTime = 0;
			errors = -10000;
			return;

		} catch (IOException ex) {
			//ex.printStackTrace();
		}
		try {
			socket.close();
		} catch (Exception ex) {
			//ex.printStackTrace();
		}
		socket = null;
		inputStream = null;
		outputStream = null;
		remainingData = 0;
		errors++;
	}

	public int getAnimCount() {
		return anIntArray1360.length;
	}

	public int getModelCount() {
		return 29191;
	}

	@Override
	public final void provide(int file) {
		provide(0, file);
	}

	public void provide(int type, int file) {
		synchronized (requests) {
			for (Resource resource = (Resource) requests.reverseGetFirst(); resource != null; resource = (Resource) requests.reverseGetNext())
				if (resource.dataType == type && resource.ID == file)
					return;

			Resource resource = new Resource();
			resource.dataType = type;
			resource.ID = file;
			resource.incomplete = true;
			synchronized (mandatoryRequests) {
				mandatoryRequests.insertHead(resource);
			}
			requests.insertHead(resource);
		}
	}

	public int getModelIndex(int i) {
		return modelIndices[i] & 0xff;
	}

	public void run() {
		try {
			while (running) {
				tick++;
				int sleepTime = 20;
				if (maximumPriority == 0 && clientInstance.indices[0] != null)
					sleepTime = 50;
				try {
					Thread.sleep(sleepTime);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				expectingData = true;
				for (int index = 0; index < 100; index++) {
					if (!expectingData)
						break;
					expectingData = false;
					loadMandatory();
					requestMandatory();
					if (uncompletedCount == 0 && index >= 5)
						break;
					loadExtra();
					if (inputStream != null)
						respond();
				}

				boolean idle = false;
				for (Resource resource = (Resource) requested.reverseGetFirst(); resource != null; resource = (Resource) requested.reverseGetNext())
					if (resource.incomplete) {
						idle = true;
						resource.loopCycle++;
						if (resource.loopCycle > 50) {
							resource.loopCycle = 0;
							request(resource);
						}
					}

				if (!idle) {
					for (Resource resource = (Resource) requested.reverseGetFirst(); resource != null; resource = (Resource) requested.reverseGetNext()) {
						idle = true;
						resource.loopCycle++;
						if (resource.loopCycle > 50) {
							resource.loopCycle = 0;
							request(resource);
						}
					}

				}
				if (idle) {
					idleTime++;
					if (idleTime > 750) {
						try {
							socket.close();
						} catch (Exception _ex) {
						}
						socket = null;
						inputStream = null;
						outputStream = null;
						remainingData = 0;
					}
				} else {
					idleTime = 0;
					loadingMessage = "";
				}
				if (Client.loggedIn && socket != null && outputStream != null && (maximumPriority > 0 || clientInstance.indices[0] == null)) {
					deadTime++;
					if (deadTime > 500) {
						deadTime = 0;
						payload[0] = 0;
						payload[1] = 0;
						payload[2] = 0;
						payload[3] = 10;
						try {
							outputStream.write(payload, 0, 4);
						} catch (IOException _ex) {
							idleTime = 5000;
						}
					}
				}
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			SignLink.reporterror("od_ex " + exception.getMessage());
		}
	}

	public void loadExtra(int type, int file) {
		if (clientInstance.indices[0] == null){
			return;
		} else if (maximumPriority == 0) {
			return;
		}
		Resource resource = new Resource();
		resource.dataType = file;
		resource.ID = type;
		resource.incomplete = false;
		synchronized (extras) {
			extras.insertHead(resource);
		}
	}

	public Resource next() {
		Resource resource;
		synchronized (complete) {
			resource = (Resource) complete.popHead();
		}
		if (resource == null)
			return null;
		synchronized (requests) {
			resource.unlinkCacheable();
		}
		if (resource.buffer == null)
			return resource;
		int read = 0;
		try {
			GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(resource.buffer));
			do {
				if (read == gzipInputBuffer.length)
					throw new RuntimeException("buffer overflow!");
				int in = gis.read(gzipInputBuffer, read, gzipInputBuffer.length - read);
				if (in == -1)
					break;
				read += in;
			} while (true);
		} catch (IOException _ex) {
			System.out.println("Failed to unzip model [" + resource.ID + "] type = " + resource.dataType);
			_ex.printStackTrace();
			return null;
		}
		resource.buffer = new byte[read];
		System.arraycopy(gzipInputBuffer, 0, resource.buffer, 0, read);

		return resource;
	}

	public int resolve(int regionX, int regionY, int type) {
		int code = (type << 8) + regionY;
		for (int area = 0; area < areas.length; area++) {			
			if (areas[area] == code) {
				if (regionX == 0) {
					return mapFiles[area] > 3535 ? -1 : mapFiles[area];
				} else {
					return landscapes[area] > 3535 ? -1 : landscapes[area];
				}
			}
		}
		return -1;
	}

	public void requestExtra(byte priority, int type, int file) {
		if (clientInstance.indices[0] == null)
			return;
		//if (versions[type][file] == 0)
		//	return;
		byte[] data = clientInstance.indices[type + 1].decompress(file);
		if(crcMatches(crcs[type][file], data))
			return;
		fileStatus[type][file] = priority;
		if (priority > maximumPriority)
			maximumPriority = priority;
		totalFiles++;
	}

	public boolean landscapePresent(int landscape) {
		for (int index = 0; index < areas.length; index++)
			if (landscapes[index] == landscape)
				return true;
		return false;
	}

	private void requestMandatory() {
		uncompletedCount = 0;
		completedCount = 0;
		for (Resource resource = (Resource) requested.reverseGetFirst(); resource != null; resource = (Resource) requested.reverseGetNext())
			if (resource.incomplete) {
				uncompletedCount++;
				if(!Configuration.JAGCACHED_ENABLED) {
					System.out.println("Error: model is incomplete or missing  [ type = " + resource.dataType + "]  [id = " + resource.ID + "]");
				}
			} else
				completedCount++;

		while (uncompletedCount < 10) {
			Resource request = (Resource) unrequested.popHead();
			if (request == null) {
				break;
			}
			try {
				if (fileStatus[request.dataType][request.ID] != 0) {
					filesLoaded++;
				}
				fileStatus[request.dataType][request.ID] = 0;
				requested.insertHead(request);
				uncompletedCount++;
				request(request);
				expectingData = true;
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public void clearExtras() {
		synchronized (extras) {
			extras.clear();
		}
	}

	private void loadMandatory() {
		Resource resource;
		synchronized (mandatoryRequests) {
			resource = (Resource) mandatoryRequests.popHead();
		}
		while (resource != null) {
			expectingData = true;
			byte data[] = null;

			if (clientInstance.indices[0] != null)
				data = clientInstance.indices[resource.dataType + 1].decompress(resource.ID);

			//CRC MATCHING
			if(Configuration.JAGCACHED_ENABLED) {
				if(!crcMatches(crcs[resource.dataType][resource.ID], data)) {
					data = null;
				}
			}

			synchronized (mandatoryRequests) {
				if (data == null) {
					unrequested.insertHead(resource);
				} else {
					resource.buffer = data;
					synchronized (complete) {
						complete.insertHead(resource);
					}
				}
				resource = (Resource) mandatoryRequests.popHead();
			}
		}
	}


	private void loadExtra() {
		while (uncompletedCount == 0 && completedCount < 10) {
			if (maximumPriority == 0)
				break;
			Resource resource;
			synchronized (extras) {
				resource = (Resource) extras.popHead();
			}
			while (resource != null) {
				if (fileStatus[resource.dataType][resource.ID] != 0) {
					fileStatus[resource.dataType][resource.ID] = 0;
					requested.insertHead(resource);
					request(resource);
					expectingData = true;
					if (filesLoaded < totalFiles)
						filesLoaded++;
					loadingMessage = "Loading extra files - " + (filesLoaded * 100) / totalFiles + "%";
					completedCount++;
					if (completedCount == 10)
						return;
				}
				synchronized (extras) {
					resource = (Resource) extras.popHead();
				}
			}
			for (int type = 0; type < 4; type++) {
				byte data[] = fileStatus[type];
				int size = data.length;
				for (int file = 0; file < size; file++)
					if (data[file] == maximumPriority) {
						data[file] = 0;
						Resource newResource = new Resource();
						newResource.dataType = type;
						newResource.ID = file;
						newResource.incomplete = false;
						requested.insertHead(newResource);
						request(newResource);
						expectingData = true;
						if (filesLoaded < totalFiles)
							filesLoaded++;
						loadingMessage = "Loading extra files - " + (filesLoaded * 100) / totalFiles + "%";
						completedCount++;
						if (completedCount == 10)
							return;
					}
			}
			maximumPriority--;
		}
	}

	public boolean highPriorityMusic(int file) {
		return musicPriorities[file] == 1;
	}

	/**
	 * Grabs the checksum of a file from the cache.
	 * @param type The type of file (0 = model, 1 = anim, 2 = midi, 3 = map).
	 * @param id The id of the file.
	 * @return
	 */
	private boolean crcMatches(int expectedValue, byte crcData[])
	{
		if(crcData == null || crcData.length < 2)
			return false;
		int length = crcData.length - 2;
		crc32.reset();
		crc32.update(crcData, 0, length);
		int crcValue = (int) crc32.getValue();
		return crcValue == expectedValue;
	}
	public void writeAll() {
		for(int i = 0; i < crcs.length; i++) {
			writeChecksumList(i);
			writeVersionList(i);
		}
	}
	public int getChecksum(int type, int id) {
		int crc = -1;
		byte[] data = clientInstance.indices[type + 1].decompress(id);
		if (data != null) {
			int length = data.length - 2;
			crc32.reset();
			crc32.update(data, 0, length);
			crc = (int) crc32.getValue();
		}
		return crc;
	}
	public int getVersion(int type, int id) {
		int version = -1;
		byte[] data = clientInstance.indices[type + 1].decompress(id);
		if (data != null) {
			int length = data.length - 2;
			version = ((data[length] & 0xff) << 8) + (data[length + 1] & 0xff);
		}
		return version;
	}
	public void writeChecksumList(int type) {
		try {
			DataOutputStream out = new DataOutputStream(new FileOutputStream(SignLink.findcachedir() + type + "_crc.dat"));
			int total = 0;
			for (int index = 0; index < clientInstance.indices[type + 1].getFileCount(); index++) {
				out.writeInt(getChecksum(type, index));
				total++;
			}
			System.out.println(type+"-"+total);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void writeVersionList(int type) {
		try {
			DataOutputStream out = new DataOutputStream(new FileOutputStream(SignLink.findcachedir() + type + "_version.dat"));
			for (int index = 0; index < clientInstance.indices[type + 1].getFileCount(); index++) {
				out.writeShort(getVersion(type, index));
			}
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}