package fi.csc.chipster.benchmark;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.RequestClientOptions;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;

public class S3Benchmark {
	
	private long t = System.currentTimeMillis();
	private String influxDbData = "";
	
	private Random rand = new Random();
	private byte[] randData;

	public S3Benchmark(HashMap<String, String> conf) throws AmazonClientException, InterruptedException, IOException {		
		
		String bucketName = conf.get("bucket-name");
		long midFileSize = Long.parseLong(conf.get("mid-file-size"));
		long bigFileSize = Long.parseLong(conf.get("big-file-size"));
		long multipartPartSize = Long.parseLong(conf.get("multipart-part-size"));
		int threads = Integer.parseInt(conf.get("threads"));
		String s3AccessKey = conf.get("s3-access-key");
		String s3SecretKey = conf.get("s3-secret-key");
		String influxUrl = conf.get("influx-url");
				
		int nSerial = Integer.parseInt(conf.get("upload-serial-count"));
		long smallFileSize = Long.parseLong(conf.get("small-file-size"));
		long dummyReadSize = Long.parseLong(conf.get("dummy-read-size"));
				
		System.out.println("bucketName " + bucketName);
		System.out.println("bigFileSize " + bigFileSize/1000.0/1000/1000 + "GB");
		System.out.println("midFileSize " + midFileSize/1000.0/1000 + "MB");
		System.out.println("multipartPartSize " + multipartPartSize/1000.0/1000 + "MB");
		System.out.println("threads " + threads);
		System.out.println("influxUrl " + influxUrl);
		System.out.println("nSerial " + nSerial);
		System.out.println("smallFileSize " + smallFileSize/1000.0 + "kB");
		System.out.println("dummyReadSize " + dummyReadSize/1000.0/1000 + "MB");
		
		
		int randSize = 128 * 1024 * 1024;
		this.randData = new byte[randSize];
		rand.nextBytes(this.randData);		
		
		File file = new File("dummy-mid");
		IOUtils.copyLarge(getStream(midFileSize), new FileOutputStream(file));				
		
		AmazonS3 s3 = getClient(s3AccessKey, s3SecretKey);		
		
		report("init", null, null);
		
		NullOutputStream os = new NullOutputStream();		
		IOUtils.copy(getStream(dummyReadSize),  os);
		report("dummy_stream", dummyReadSize, null);
						
		upload(s3, bucketName, getStream(midFileSize), "dummy-mid", midFileSize);
		report("upload,size=" + midFileSize, midFileSize, null);
		
		download(s3, bucketName, "dummy-mid");
		report("download", midFileSize, null);		
		
		uploadMultipartTest(s3, bucketName, "dummy-big-multipart", bigFileSize, multipartPartSize, threads);		
		report("upload_multipart,size=" + bigFileSize + ",threads=" + threads, bigFileSize, null);
				
		
		for (int i = 0; i < nSerial; i++) {
			upload(s3, bucketName, getStream(smallFileSize), "dummy-4k-" + i, smallFileSize);
		}
		report("upload_4k_serial", smallFileSize * threads, nSerial);
		
		for (int i = 0; i < nSerial; i++) {
			download(s3, bucketName, "dummy-4k-" + i);
		}
		report("download_4k_serial", smallFileSize * threads, nSerial);
		

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		for (int i = 0; i < threads; i++) {
			final int fi = i;
			pool.execute(new Runnable() {				
				@Override
				public void run() {
					upload(s3, bucketName, getStream(smallFileSize), "dummy-4k-" + fi, smallFileSize);					
				}
			});
			
		}
		pool.shutdown();
		pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);		
		report("upload_4k_parallel,threads=" + threads, smallFileSize * threads, threads);
		
		pool = Executors.newFixedThreadPool(threads);
		for (int i = 0; i < threads; i++) {
			final int fi = i;
			pool.execute(new Runnable() {				
				@Override
				public void run() {
					download(s3, bucketName, "dummy-4k-" + fi);					
				}
			});
			
		}
		pool.shutdown();
		pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);		
		report("download_4k_parallel,threads=" + threads, smallFileSize * threads, threads);		
		
		System.out.println(influxDbData);
		
		if (influxUrl != null) {
			HttpUriRequest post = RequestBuilder.post(influxUrl).setEntity(new StringEntity(influxDbData)).build();			
			System.out.println(HttpClients.createDefault().execute(post).getStatusLine());
		}
	}
	
	private void report(String task, Long bytes, Integer requests) {
		long dt = (System.currentTimeMillis() - t);		
		System.out.print(task + " " + dt + "ms ");
		reportToInfluxDb("time", task, "" + dt);
		
		if (bytes != null) {
			float speed = bytes * 1000f / dt;
			System.out.print(speed/1024/1024 + " MB/s ");
			// String.format to prevent scientific notation
			reportToInfluxDb("throughput", task, String.format("%.12f", speed));
		}
		if (requests != null) {
			float rps = requests * 1000f / dt;
			System.out.print(rps + " requests/s");
			reportToInfluxDb("requests_per_second", task, String.format("%.12f", rps));
		}				
		System.out.println();
		t = System.currentTimeMillis();
	}
	
	private void reportToInfluxDb(String type, String task, String value) {
		influxDbData += type + ",task=" + task + " value=" + value + " " + System.currentTimeMillis() * 1000_000 + "\n";
	}

	private InputStream getStream(long smallFileSize) {
		return new BoundedInputStream(
						new DummyInputStream(), 
				smallFileSize);
	}

	private AmazonS3 getClient(String access, String secret) {

		AWSCredentials credentials = new BasicAWSCredentials(access, secret);

		ClientConfiguration clientConfig = new ClientConfiguration()
				.withSignerOverride("S3SignerType")
				.withTcpKeepAlive(true);
		
		EndpointConfiguration endpointConf = new EndpointConfiguration("https://object.pouta.csc.fi", "regionOne");
		
		AmazonS3 s3 = AmazonS3ClientBuilder.standard()
				.withClientConfiguration(clientConfig)
				.withEndpointConfiguration(endpointConf)
				.withCredentials(new AWSStaticCredentialsProvider(credentials))
				.build();

		return s3;
	}
	
	private void upload(AmazonS3 s3, String bucketName, InputStream smallFile, String keyName, long smallFileSize) {

	        ObjectMetadata meta = new ObjectMetadata();
	        meta.setContentLength(smallFileSize);
	        // required by s3.putObject()
	        int buffSize = RequestClientOptions.DEFAULT_STREAM_BUFFER_SIZE;
	        BufferedInputStream is = new BufferedInputStream(smallFile, buffSize); 
	        
	        try {
	            s3.putObject(bucketName, keyName, is, meta);
	        } catch (AmazonServiceException e) {
	        	e.printStackTrace();
	            System.exit(1);
	        }
	}
	
	private void uploadMultipartTest(AmazonS3 s3Client, String bucketName, String keyName, long contentLength, long partSize, int threads) throws InterruptedException {		 

        try {
                       
            // Create a list of ETag objects. You retrieve ETags for each object part uploaded,
            // then, after each individual part has been uploaded, pass the list of ETags to 
            // the request to complete the upload.
            List<PartETag> partETags = new ArrayList<PartETag>();

            long t = System.currentTimeMillis();
            System.out.println("init multipart upload");
            // Initiate the multipart upload.
            InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucketName, keyName);
            InitiateMultipartUploadResult initResponse = s3Client.initiateMultipartUpload(initRequest);
            
    		System.out.println((System.currentTimeMillis() - t) + "ms ");
    		t = System.currentTimeMillis();

    		ExecutorService pool = Executors.newFixedThreadPool(threads);
    		
            // Upload the file parts.
            long filePosition = 0;
            for (int i = 1; filePosition < contentLength; i++) {
            	
            	final int fi = i;
                // Because the last part could be less than 5 MB, adjust the part size as needed.
                final long fPartSize = Math.min(partSize, (contentLength - filePosition));
            	
            	pool.execute(new Runnable() {

					@Override
					public void run() {

		                long t = System.currentTimeMillis();
		
		                // Create the request to upload a part.
		                UploadPartRequest uploadRequest = new UploadPartRequest()
		                        .withBucketName(bucketName)
		                        .withKey(keyName)
		                        .withUploadId(initResponse.getUploadId())
		                        .withPartNumber(fi)
		                        .withInputStream(getStream(fPartSize))
		                        .withPartSize(fPartSize);
		
		                // Upload the part and add the response's ETag to our list.
		                UploadPartResult uploadResult = s3Client.uploadPart(uploadRequest);
		                partETags.add(uploadResult.getPartETag());
		
		                
		                System.out.println("uploaded part " + fi + ", " + fPartSize / 1024 / 1024 + " MB: " + (System.currentTimeMillis() - t) + "ms " + ((fPartSize / 1024 / 1024 * 1000) / (System.currentTimeMillis() - t)) + " MB/s");			}            		
            	});

            	filePosition += partSize;
            }
            
    		pool.shutdown();
    		pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);

    		t = System.currentTimeMillis();
            System.out.println("complete multipart upload");
            // Complete the multipart upload.
            CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(bucketName, keyName,
                    initResponse.getUploadId(), partETags);
            s3Client.completeMultipartUpload(compRequest);
            
            System.out.println((System.currentTimeMillis() - t) + "ms ");
    		t = System.currentTimeMillis();
        }
        catch(AmazonServiceException e) {
            // The call was transmitted successfully, but Amazon S3 couldn't process 
            // it, so it returned an error response.
            e.printStackTrace();
        }
        catch(SdkClientException e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace();
        }
	}
    
    public void download(AmazonS3 s3, String bucketName, String keyName) {
    	
    	try {
    	    S3Object o = s3.getObject(bucketName, keyName);    	    
    	    S3ObjectInputStream s3is = o.getObjectContent();
    	    NullOutputStream fos = new NullOutputStream();    	    
    	    IOUtils.copy(s3is, fos);
    	    s3is.close();
    	    fos.close();
    	} catch (AmazonServiceException e) {
    	    System.err.println(e.getErrorMessage());
    	    System.exit(1);
    	} catch (FileNotFoundException e) {
    	    System.err.println(e.getMessage());
    	    System.exit(1);
    	} catch (IOException e) {
    	    System.err.println(e.getMessage());
    	    System.exit(1);
    	}
    }

    public class DummyInputStream extends InputStream {

        private boolean closed = false;

		@Override
        public int read() throws IOException {
            checkOpen();
            byte[] array = new byte[1];
            read(array);
            return array[0];
        }

        @Override
        public int read(byte[] data, int offset, int length) throws IOException {
            checkOpen();
            // read from the random location of the random data array
            System.arraycopy(randData, rand.nextInt(randData.length - length), data, offset, length);
            return length;
        }

        @Override
        public int read(byte[] data) throws IOException {
            return read(data, 0, data.length);
        }

        @Override
        public long skip(long bytesToSkip) throws IOException {
            checkOpen();
            // It's all random so skipping has no effect.
            return bytesToSkip;
        }

        @Override
        public void close() {
            this.closed = true;
        }

        private void checkOpen() throws IOException {
            if (closed) {
                throw new IOException("Input stream closed");
            }
        }

        @Override
        public int available() {
            // Limited only by available memory and the size of an array.
            return Integer.MAX_VALUE;
        }
    }
    
	private static HashMap<String, String> readFile(String confFilePath) throws FileNotFoundException, YamlException {
		
		HashMap<String, String> conf = new HashMap<>();
		YamlReader reader = new YamlReader(new FileReader(confFilePath));
		Object object = reader.read();
		if (object instanceof Map) {
			@SuppressWarnings("rawtypes")
			Map confFileMap = (Map) object;

			for (Object key : confFileMap.keySet()) {
				Object valueObj = confFileMap.get(key);
				String value = valueObj != null ? valueObj.toString() : null;
				conf.put(key.toString(), value);
			}
		} else {
			throw new RuntimeException("configuration file should be a yaml map, but it is " + object);
		}

		return conf;
	}
    
	public static void main(String args[]) throws AmazonClientException, InterruptedException, IOException {
		if (args.length != 1) {
			throw new IllegalArgumentException("Usage: " + S3Benchmark.class.getSimpleName() + ".jar conf-file-path.yaml");
		}
		HashMap<String, String> conf = readFile(args[0]);
		new S3Benchmark(conf);
	}
}
