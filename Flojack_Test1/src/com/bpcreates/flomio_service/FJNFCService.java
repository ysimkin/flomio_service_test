package com.bpcreates.flomio_service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.MediaRecorder.AudioSource;
import android.os.Process;
import android.util.Log;

public class FJNFCService extends IntentService {
	
	public interface Const {
	    int SAMPLE_RATE = 44100;    // in Hz
	    int SAMPLES = 512;          // number of samples
	    int BITS = 9;               // a power of two such that 2^b is the number_of_samples
	    int REPEAT = 3;             // repeat each signal
	    int OFFSET = 60;            // number of un-usable bin on the spectrum's lower end
	}
//    private final static int ZERO_TO_ONE_THRESHOLD = 0;        // threshold used to detect start bit
//
//    private final static int SAMPLESPERBIT = 13;               // (44100 / HIGHFREQ)  // how many samples per UART bit
//    private final static int SHORT =  (SAMPLESPERBIT/2 + SAMPLESPERBIT/4);
//    private final static int LONG =  (SAMPLESPERBIT + SAMPLESPERBIT/2);
//
//    private final static int HIGHFREQ = 3392;                  // baud rate. best to take a divisible number for 44.1kS/s
//    private final static int LOWFREQ = (HIGHFREQ / 2);
//
//    private final static int NUMSTOPBITS = 20;                 // number of stop bits to send before sending next value.
//    private final static int NUMSYNCBITS = 4;                  // number of ones to send before sending first value.
//
//    private final static int SAMPLE_NOISE_CEILING = 100000;    // keeping running average and filter out noisy values around 0
//    private final static int SAMPLE_NOISE_FLOOR = -100000;     // keeping running average and filter out noisy values around 0
//
//    private final static double MESSAGE_SYNC_TIMEOUT = 0.500;  // seconds

    private String LOG_TAG = "FJNFCService";
    
    private AudioRecord remoteIOUnit;

    private final static int RATE = 44100;
    private final static int BUFFER_SIZE = 4096;
    private final static int BUFFER_SIZE_IN_MS = 1000 * BUFFER_SIZE / RATE; //3000
    private final static int CHUNK_SIZE_IN_SAMPLES = 4096; // = 2 ^
    private final static int CHUNK_SIZE_IN_MS = 1000 * CHUNK_SIZE_IN_SAMPLES / RATE;
    private final static int BUFFER_SIZE_IN_BYTES = RATE * BUFFER_SIZE_IN_MS / 1000 * 2;
    private final static int CHUNK_SIZE_IN_BYTES = RATE * CHUNK_SIZE_IN_MS / 1000 * 2;

    private int inNumberFrames = 0; // a frame is compose of one mic audio sample.  Stereo mics can have 2 channels
    private int[] tailoredAudioData = new int[0];
    private long processingTime = 0;

    private enum uart_state {
        STARTBIT,               //(0)
        SAMEBIT,                //(1)
        NEXTBIT,                //(2)
        STOPBIT,                //(3)
        STARTBIT_FALL,          //(4)
        DECODE_BYTE_SAMPLE      //(5)
    }
    
    private Context context;

   
    short [] buffer;
    public FJNFCService() {
        super("FJNFC Service");
        L.d("FJNFC constructor called.");
     }

     @Override
     public void onStart(Intent intent,  int startId) {
    	 super.onStart(intent, startId);
    	 L.d("start called");
    	 new Listen().start();
     }
    
     
     @Override
     public void onHandleIntent(Intent intent) {
  	   try{
  		   L.d("handle intent called");
  	   
  	   }catch(Exception e){
  		   
  	   }
     }

    public class Listen extends Thread {
        
    	int bufferSize = BUFFER_SIZE;
        	
        public void run(){
        	L.d("thread starting");
        	Process
            .setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
		     
		    int minBufferSize = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
		            AudioFormat.ENCODING_PCM_16BIT);
		    if (minBufferSize > bufferSize) {
		        bufferSize = minBufferSize;
		    }
		    remoteIOUnit = new AudioRecord(AudioSource.MIC, RATE,
		            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
		    if (remoteIOUnit.getState() != AudioRecord.STATE_INITIALIZED) {
		        L.d("Can't initialize AudioRecord");  // Needed <uses-permission android:name="android.permission.RECORD_AUDIO"/>
		        return;
		    }
		    
		    L.d("Set buffer size to " + bufferSize);
        
        
        
        /*SIMKIN
         * 
         */
        
        remoteIOUnit.setPositionNotificationPeriod(Const.SAMPLES);
        remoteIOUnit.setRecordPositionUpdateListener(new OnRecordPositionUpdateListener() {
            @Override
            public void onPeriodicNotification(AudioRecord recorder) {
            	L.d("NOTIFIED");
            	
                ///*short[]*/ buffer = new short[Const.SAMPLES];//buffers[++lastBuffer % buffers.length];
                //recorder.read(buffer, 0, Const.SAMPLES);
                
                String strBuffer = "";
                for(int i = 0; i< Const.SAMPLES; i++)
                	strBuffer += buffer[i];
                L.d("read buffer " + strBuffer);
            	//}catch(Exception e){
            		//L.e("FAILED " + e);
            	//}
            	
            }

            @Override
            public void onMarkerReached(AudioRecord recorder) {
            	L.d("MARKER REACHED");
            }
        });
        
        
        
        
        
        
        
        
        

        remoteIOUnit.startRecording();
        
        /*final short[] */buffer = new short[Const.SAMPLES];
        
        while (!Thread.interrupted()) {
        	String strBuffer = "";
            int offset = 0;

            while (offset < Const.SAMPLES && !Thread.interrupted()) {

                offset += remoteIOUnit.read(buffer, offset, Const.SAMPLES - offset);

            }
            if (offset == Const.SAMPLES) {
            	try{
	            	for(int i = 0; i< offset; i++)
	                	strBuffer += buffer[i];
	                //L.d("read buffer " + strBuffer);
            	}catch(Exception e){
            		L.e("FAILED " + e);
            	}

            }
        }
        
        
       /* int scanCounter = 0;
        while (true) {
            inNumberFrames = 0; // zero out the number of frames to adjust the buffer captured to size
            short[] audioData = new short[BUFFER_SIZE_IN_BYTES / 2];
            remoteIOUnit.read(audioData, 0, CHUNK_SIZE_IN_BYTES / 2);
            if((inNumberFrames = remoteIOUnit.read(audioData, 0, CHUNK_SIZE_IN_BYTES / 2)) == AudioRecord.ERROR_BAD_VALUE){
               
               L.e("READ: BAD VALUE GIVEN");
               break;
            }
            long t = System.currentTimeMillis();
            if(tailoredAudioData.length != inNumberFrames){
                tailoredAudioData = new int[inNumberFrames];
            }
            for(int i  = 0; i < inNumberFrames; i++){
                tailoredAudioData[i] = (int) (audioData[i]/64.0);
            }
            String parsedData = floJackAURenderCallback(tailoredAudioData);
            processingTime = (System.currentTimeMillis()-t);
            //mListener.onData(parsedData); // post UUID string to Host application
            //mListener.onData("1234567890");
            if(++scanCounter % 10 == 0){
        		L.d("scan received");
        		scanCounter = 0;
        		//msg = "1234567890";
        		broadcastScan("1234567890");
        	}
            
        }*/
        
        L.d("done listening, quitting");
        remoteIOUnit.stop();
        remoteIOUnit.release();
        }
        
    }
    
    
    private void broadcastScan(String msg){
    	Intent i = new Intent();        	
    	i.setAction("com.restock.serialmagic.gears.action.SCAN");
    	i.putExtra("scan", msg);    	
    	FJNFCService.this.sendBroadcast(i);
    }
    
    

    private String floJackAURenderCallback(int[] audioData) {
        // TX vars
        int byteCounter = 1;
        uart_state decoderState = uart_state.STARTBIT;
        int lastSample = 0;
        int lastPhase2 = 0;
        short parityTx = 0;
        int phase = 0;
        int phase2 = 0;
        int sample = 0;

        // UART decoding
        int bitNum = 0;
        boolean parityGood = false;
        short uartByte = 0;
        //L.d("AUDIO DATA LENDTH = " + audioData.length);

//        /************************************
//         * UART Decoding
//         ************************************/
//        for(int frameIndex = 0; frameIndex<inNumberFrames; frameIndex++) {
//            float raw_sample = audioData[frameIndex];
//            mListener.onData(String.format("%8ld, %8.0f\n", phase2, raw_sample));
//
//            if(decoderState == DECODE_BYTE_SAMPLE )
//                LogDecoderVerbose(@"%8ld, %8.0f, %d\n", phase2, raw_sample, frameIndex);
//
//            phase2 += 1;
//            if (raw_sample < ZERO_TO_ONE_THRESHOLD) {
//                sample = self->_logicZero;
//                sample_avg_low = (sample_avg_low + raw_sample)/2;
//            }
//            else {
//                sample = self->_logicOne;
//                sample_avg_high = (sample_avg_high + raw_sample)/2;
//            }
//
//            if ((sample != lastSample) && (sample_avg_high > SAMPLE_NOISE_CEILING) && (sample_avg_low < SAMPLE_NOISE_FLOOR)) {
//                // we have a transition
//                SInt32 diff = phase2 - lastPhase2;
//                switch (decoderState) {
//                    case STARTBIT:
//                        if (lastSample == 0 && sample == 1) {
//                            // low->high transition. Now wait for a long period
//                            decoderState = STARTBIT_FALL;
//                        }
//                        break;
//                    case STARTBIT_FALL:
//                        if ((SHORT < diff) && (diff < LONG)) {
//                            // looks like we got a 1->0 transition.
//                            LogDecoder(@"Received a valid StartBit \n");
//                            decoderState = DECODE_BYTE_SAMPLE;
//                            bitNum = 0;
//                            parityRx = 0;
//                            uartByte = 0;
//                        }
//                        else {
//                            // looks like we didn't
//                            decoderState = STARTBIT;
//                        }
//                        break;
//                    case DECODE_BYTE_SAMPLE:
//                        if ((SHORT < diff) && (diff < LONG)) {
//                            // We have a valid sample.
//                            if (bitNum < 8) {
//                                // Sample is part of the byte
//                                //LogDecoder(@"Bit %d value %ld diff %ld parity %d\n", bitNum, sample, diff, parityRx & 0x01);
//                                uartByte = ((uartByte >> 1) + (sample << 7));
//                                bitNum += 1;
//                                parityRx += sample;
//                            }
//                            else if (bitNum == 8) {
//                                // Sample is a parity bit
//                                if(sample != (parityRx & 0x01)) {
//                                    LogError(@" -- parity %ld,  UartByte 0x%x\n", sample, uartByte);
//                                    //decoderState = STARTBIT;
//                                    parityGood = false;
//                                    bitNum += 1;
//                                }
//                                else {
//                                    LogTrace(@" ++ UartByte: 0x%x\n", uartByte);
//                                    //LogTrace(@" +++ good parity: %ld \n", sample);
//                                    parityGood = true;
//                                    bitNum += 1;
//                                }
//                            }
//                            else {
//                                // Sample is stop bit
//                                if (sample == 1) {
//                                    // Valid byte
//                                    //LogTrace(@" +++ stop bit: %ld \n", sample);
////                                if(frameIndex > (80))
////                                {
////                                    //LogError(@"%f\n", raw_sample);
////                                    for(int k=frameIndex-80; k<256; k++)
////                                    {
////                                        NSLog(@"%d\n", left_audio_channel[k]);
////                                    }
////                                    NSLog(@"%f\n", raw_sample);
////                                }
//                                }
//                                else {
//                                    // Invalid byte
//                                    LogError(@" -- StopBit: %ld UartByte 0x%x\n", sample, uartByte);
//                                    parityGood = false;
//                                }
//
//                                // Send bye to message handler
//                                //NSAutoreleasePool *autoreleasepool = [[NSAutoreleasePool alloc] init];
//                                @autoreleasepool {
//                                    [self handleReceivedByte:uartByte withParity:parityGood atTimestamp:CACurrentMediaTime()];
//                                }
//                                //[autoreleasepool release];
//
//                                decoderState = STARTBIT;
//                            }
//                        }
//                        else if (diff > LONG) {
//                            LogDecoder(@"Diff too long %ld\n", diff);
//                            decoderState = STARTBIT;
//                        }
//                        else {
//                            // don't update the phase as we have to look for the next transition
//                            lastSample = sample;
//                            continue;
//                        }
//
//                        break;
//                    default:
//                        break;
//                }
//                lastPhase2 = phase2;
//            }
//            lastSample = sample;
//        } //end: for(int j = 0; j < inNumberFrames; j++)


        return "";
    }


	

//    private void handleReceivedByte(byte myByte, boolean parityGood, double timestamp){
//
//    }
//    private void sendFloJackConnectedStatusToDelegate() {
//
//    }
//    private void clearMessageBuffer() {
//
//    }
//    private void checkIfVolumeLevelMaxAndNotifyDelegate() {
//
//    }
//    private void disableDeviceSpeakerPlayback() {
//
//    }
//    private void enableDeviceSpeakerPlayback() {
//
//    }
//    private void sendByteToHost(byte theByte) {
//
//    }
//    private void sendMessageDataToHost(ByteBuffer messageData) {
//
//    }
//    private void setOutputAmplitudeHigh() {
//
//    }
//    private void setOutputAmplitudeNormal() {
//
//    }
//    public byte getDeviceInterByteDelay() {
//
//    }
//    public byte getDeviceLogicOneValue() {
//
//    }
//    public byte getDeviceLogicZeroValue() {
//
//    }
}

