package com.bpcreates.flomio_service;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.FloatMath;
import android.util.Log;
import android.widget.Toast;

public class FJNFCService extends IntentService {
    // Basic Audio system constants
    private final static int RATE = 44100;                     // rate at which samples are collected
    private final static int SAMPLES = 1 << 10;                 // number of samples to process at one time

    private final static int ZERO_TO_ONE_THRESHOLD = 0;         // threshold used to detect start bit
    private final static int SAMPLESPERBIT = 13;                // (44100 / HIGHFREQ)  // how many samples per UART bit
    private final static int SHORT =  (SAMPLESPERBIT/2 + SAMPLESPERBIT/4);
    private final static int LONG =  (SAMPLESPERBIT + SAMPLESPERBIT/2);
    private final static float HIGHFREQ = 3392;                 // baud rate. best to take a divisible number for 44.1kS/s
    private final static float LOWFREQ = (HIGHFREQ / 2);
    private final static int NUMSTOPBITS = 20;                  // number of stop bits to send before sending next value.
    private final static int NUMSYNCBITS = 4;                   // number of ones to send before sending first value.
    private final static int SAMPLE_NOISE_CEILING = 4000;       // keeping running average and filter out noisy values around 0
    private final static int SAMPLE_NOISE_FLOOR = -4000;        // keeping running average and filter out noisy values around 0
    private final static long MESSAGE_SYNC_TIMEOUT = 500;    // milliseconds

    // Message Length Boundaries
    private final static int MIN_MESSAGE_LENGTH = 3;   //TODO: change to 4
    private final static int MAX_MESSAGE_LENGTH = 255;
    private final static int CORRECT_CRC_VALUE = 0;
    // Message Protocol
    private final static int FLOJACK_MESSAGE_OPCODE_POSITION =          0;
    private final static int FLOJACK_MESSAGE_LENGTH_POSITION =          1;
    private final static int FLOJACK_MESSAGE_SUB_OPCODE_POSITION =      2;
    private final static int FLOJACK_MESSAGE_ENABLE_POSITION =          3;

    private final static int FJ_TAG_UID_DATA_POS =                      3;
    private final static int FJ_BLOCK_RW_MSG_DATA_LENGTH_POS =          4;
    private final static int FJ_BLOCK_RW_MSG_DATA_POS =                 5;

    private final static int FLOJACK_MESSAGE_OPCODE_LENGTH =            1;
    private final static int FLOJACK_MESSAGE_LENGTH_LENGTH =            1;
    private final static int FLOJACK_MESSAGE_SUB_OPCODE_LENGTH =        1;
    private final static int FLOJACK_MESSAGE_ENABLE_LENGTH =            1;
    private final static int FLOJACK_MESSAGE_CRC_LENGTH =               1;

    private final static int FJ_BLOCK_RW_MSG_DATA_LENGTH_LEN =          1;
    private final static int FJ_BLOCK_RW_MSG_DATA_LEN =                 1;

    // Audio Unit attributes
    private AudioRecord remoteInputUnit;
    private AudioTrack remoteOutputUnit;
    private long processingTime = 0;
    private byte logicOne, logicZero;
    private int outputAmplitude;

    // Audio Unit attributes shared across classes
    private ShortBuffer inAudioData;
    private ShortBuffer outAudioData;
    //private short[] inAudioData;
    private long timeTracker;

    // NFC Service state variables
    private byte byteForTX;
    private boolean byteQueuedForTX;
    private boolean currentlySendingMessage;
    private boolean muteEnabled;

    // Message handling variables
    private long lastByteReceivedAtTime;
    private byte messageCRC;
    private ByteBuffer messageReceiveBuffer;
    private int messageLength;
    private boolean messageValid;

    // TX vars
    private short parityTx = 0;
    private int phase = 0;

    // UART encode
    private boolean comm_sync_in_progress = false;
    private byte currentBit = 1;
    private uart_state encoderState = uart_state.NEXTBIT;
    private int nextPhaseEnc = SAMPLESPERBIT;
    private int phaseEnc = 0;
    private byte uartByteTx = 0x0;
    private int uartBitTx = 0;
    private int uartSyncBitTx = 0;
    private float[] uartBitEnc = new float[SAMPLESPERBIT];

    // TX vars
    private uart_state decoderState = uart_state.STARTBIT;
    private int lastSample = 0;
    private int lastPhase2 = 0;
    private int phase2 = 0;
    private byte sample = 0;
    private Context context;

    // UART decoding
    private int bitNum = 0;
    private boolean parityGood = false;
    private byte parityRx = 0;
    private byte uartByte = 0;
    private float sample_avg_low = 0;
    private float sample_avg_high = 0;

    private enum uart_state {
        STARTBIT,               //(0)
        SAMEBIT,                //(1)
        NEXTBIT,                //(2)
        STOPBIT,                //(3)
        STARTBIT_FALL,          //(4)
        DECODE_BYTE_SAMPLE      //(5)
    }

    private String LOG_TAG = "FJNFCService";

    public FJNFCService() {
        super(FJNFCService.class.getSimpleName());
        Log.d(LOG_TAG, "constructor called.");

        // Logic high/low varies based on host device
        logicOne = getDeviceLogicOneValue();
        logicZero = getDeviceLogicZeroValue();
        muteEnabled = false;
        currentlySendingMessage = false; // TODO: toggle flag in send(byte) queuing system
        byteForTX = (byte) 0xAA;  // TODO: put send(byte) queuing system in place (use Handler?)
        outputAmplitude = (1<<24);  // TODO: create accessor for setting normal and high amplitude

        messageReceiveBuffer = ByteBuffer.allocate(MAX_MESSAGE_LENGTH);
        lastByteReceivedAtTime = (long) System.currentTimeMillis();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.d(LOG_TAG, "onStart called");

        // a frame is composed of one audio sample. Stereo can have 2 channels
        int playBufferSize = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (SAMPLES > playBufferSize) {
            Log.e(LOG_TAG, "your SAMPLES size is too large for the playing audio buffer.");
            return;
        }
        remoteOutputUnit = new AudioTrack(AudioManager.STREAM_DTMF, RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, playBufferSize,
                AudioTrack.MODE_STREAM);
        if (remoteOutputUnit.getState() != AudioTrack.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "can't initialize AudioTrack");  // TODO needs some permission?
            return;
        }

        // Set Number of frames to be 4 times larger than the minimum audio capture size.  This
        // is to allow for small chunks of the input samples to be processed while the others
        // continue to be created.  This approach allows for the FJNFCService thread to not
        // hog all the processing time and make the UI Threads unresponsive.
        int recBufferSize = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (SAMPLES > recBufferSize) {
            Log.e(LOG_TAG, "your SAMPLES size is too large for the recorded audio buffer.");
            return;
        }

        remoteInputUnit = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recBufferSize);
        if (remoteInputUnit.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "can't initialize AudioRecord");  // Needed <uses-permission android:name="android.permission.RECORD_AUDIO"/>
            return;
        }

        remoteInputUnit.startRecording();
        remoteOutputUnit.play();
        new FJNFCListener().start();
    }

    @Override
    public void onHandleIntent(Intent intent) {
        try{
            Log.d(LOG_TAG,"handle intent called");
        }catch(Exception e){
            // intentionally left empty
        }
    }

    public class FJNFCListener extends Thread {

        FJNFCListener() {
            inAudioData = ShortBuffer.allocate(SAMPLES);
//            outAudioData = ByteBuffer.allocate(SAMPLES);
//            inAudioData = new short[SAMPLES];
        }

        public void run() {
            Log.i(LOG_TAG, "starting thread to capture samples");

            // Make this thread the highest priority in order to capture a continuous stream of data
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            while (!Thread.interrupted()) {
//                timeTracker = System.currentTimeMillis();
//                if (remoteOutputUnit.write(outAudioData.array(), 0, SAMPLES) < SAMPLES) {
//                    Log.e(LOG_TAG, "we didn't write enough samples to playback audio buffer.");
//                    break;
//                }
//                flojackAUOutputCallback(outAudioData);
//                processingTime = (System.currentTimeMillis() - timeTracker);
//                Log.d(LOG_TAG, String.format("played samples in %dms", processingTime));

//                timeTracker = System.currentTimeMillis();
                if (remoteInputUnit.read(inAudioData.array(), 0, SAMPLES) < SAMPLES) {
                    Log.e(LOG_TAG, "we didn't read enough samples from recorded audio buffer.");
                    break;
                }
                flojackAUInputCallback(inAudioData);
//                processingTime = (System.currentTimeMillis()-timeTracker);
//                Log.d(LOG_TAG, String.format("processed samples in %dms", processingTime));
            }
            remoteOutputUnit.stop();     // stop playing date out
            remoteInputUnit.stop();      // stop sampling data in
            remoteOutputUnit.release();  // let go of playBuffer
            remoteInputUnit.release();   // let go of recBuffer
        }
    }

    private void flojackAUInputCallback(ShortBuffer inData) {

        /************************************
         * UART Decoding
         ************************************/
        for (int frameIndex = 0; frameIndex<inData.capacity(); frameIndex++) {
            float raw_sample = inData.get(frameIndex);
//            if (decoderState==uart_state.DECODE_BYTE_SAMPLE) {
//                Log.d(LOG_TAG, String.format("%8d, %8.0f %s\n", phase2, raw_sample,
//                        "Decode "+frameIndex));
//            }

            phase2 += 1;
            if (raw_sample < ZERO_TO_ONE_THRESHOLD) {
                sample = logicZero;
                sample_avg_low = (sample_avg_low + raw_sample)/2;
            }
            else {
                sample = logicOne;
                sample_avg_high = (sample_avg_high + raw_sample)/2;
            }

            if ((sample != lastSample) && (sample_avg_high > SAMPLE_NOISE_CEILING) && (sample_avg_low < SAMPLE_NOISE_FLOOR)) {
                // we have a transition
                int diff = phase2 - lastPhase2;
                switch (decoderState) {
                    case STARTBIT:
                        if (lastSample == 0 && sample == 1) {
                            // low->high transition. Now wait for a long period
                            decoderState = uart_state.STARTBIT_FALL;
                        }
                        break;
                    case STARTBIT_FALL:
                        if ((SHORT < diff) && (diff < LONG)) {
                            // looks like we got a 1->0 transition.
                            Log.d(LOG_TAG, String.format("Received a valid StartBit \n"));
                            decoderState = uart_state.DECODE_BYTE_SAMPLE;
                            bitNum = 0;
                            parityRx = 0;
                            uartByte = 0;
                        }
                        else {
                            // looks like we didn't
                            decoderState = uart_state.STARTBIT;
                        }
                        break;
                    case DECODE_BYTE_SAMPLE:
                        if ((SHORT < diff) && (diff < LONG)) {
                            // We have a valid sample.
                            if (bitNum < 8) {
                                // Sample is part of the byte
                                uartByte = (byte)(((uartByte >> 1)&0x7F) + (sample << 7));
                                bitNum += 1;
                                parityRx += sample;
                            }
                            else if (bitNum == 8) {
                                // Sample is a parity bit
                                if(sample != (parityRx & 0x01)) {
                                    Log.d(LOG_TAG, String.format(" -- parity %d,  UartByte 0x%x\n", sample, uartByte));
                                    parityGood = false;
                                    bitNum += 1;
                                }
                                else {
                                    Log.d(LOG_TAG, String.format(" ++ UartByte: 0x%x\n", uartByte));
                                    parityGood = true;
                                    bitNum += 1;
                                }
                            }
                            else {
                                // Sample is stop bit
                                if (sample == 1) {
                                    // Valid byte
                                    Log.d(LOG_TAG, String.format(" +++ stop bit: %d \n", sample));
                                    // Send byte to message handler
                                    handleReceivedByte(uartByte, parityGood, System.currentTimeMillis());
                                }
                                else {
                                    // Invalid byte
                                    Log.d(LOG_TAG, String.format(" -- StopBit: %d UartByte 0x%x\n", sample, uartByte));
                                    parityGood = false;
                                }
                                decoderState = uart_state.STARTBIT;
                            }
                        }
                        else if (diff > LONG) {
                            Log.d(LOG_TAG, String.format("Diff too long %d\n", diff));
                            decoderState = uart_state.STARTBIT;
                        }
                        else {
                            // don't update the phase as we have to look for the next transition
                            lastSample = sample;
                            continue;
                        }
                        break;
                    default:
                        break;
                }
                lastPhase2 = phase2;
            }
            lastSample = sample;
        } //end: for(int j = 0; j < inNumberFrames; j++)

        return;
    }

    private void flojackAUOutputCallback(ShortBuffer outData) {

        if (muteEnabled == false) {
            /*******************************
             * Generate 22kHz Tone
             *******************************/
            double waves;
            for (int j = 0; j < outData.capacity(); j++) {
                waves = 0;
                waves += FloatMath.sin((float)Math.PI * phase+0.5f); // nfcService should be 22.050kHz
                waves *= outputAmplitude; // <--------- make sure to divide by how many waves you're stacking

                outData.put(j,(short)waves);
                phase++;
            }

            /*******************************
             * UART Encoding
             *******************************/
            for(int j = 0; j<outData.capacity() && currentlySendingMessage == true; j++) {
                if (phaseEnc >= nextPhaseEnc) {
                    if(byteQueuedForTX == true && uartBitTx >= NUMSTOPBITS && comm_sync_in_progress == false) {
                        comm_sync_in_progress = true;
                        encoderState = uart_state.NEXTBIT;
                        uartSyncBitTx = 0;
                    }
                    else if (byteQueuedForTX == true && uartBitTx >= NUMSTOPBITS && uartSyncBitTx >= NUMSYNCBITS) {
                        encoderState = uart_state.STARTBIT;
                    }
                    else {
                        encoderState = uart_state.NEXTBIT;
                    }
                } //end: if (phaseEnc >= nextPhaseEnc)

                switch (encoderState) {
                    case STARTBIT:
                    {
                        uartByteTx = byteForTX;
                        byteQueuedForTX = false;

                        Log.d(LOG_TAG, String.format("uartByteTx: 0x%x\n", uartByteTx));

                        uartBitTx = 0;
                        parityTx = 0;

                        encoderState = uart_state.NEXTBIT;
                        // break;   // fall through intentionally
                    }
                    case NEXTBIT:
                    {
                        byte nextBit;
                        if (uartBitTx == 0) {
                            // start bit
                            nextBit = 0;
                        } else {
                            if (uartBitTx == 9) {
                                // parity bit
                                nextBit = (byte)(parityTx & 0x01);
                            }
                            else if (uartBitTx >= 10) {
                                // stop bit
                                nextBit = 1;
                            }
                            else {
                                nextBit = (byte)((uartByteTx >> (uartBitTx - 1)) & 0x01);
                                parityTx += nextBit;
                            }
                        }
                        if (nextBit == currentBit) {
                            if (nextBit == 0) {
                                for (int p = 0; p<SAMPLESPERBIT; p++) {
                                    uartBitEnc[p] = -FloatMath.sin((float)Math.PI * 2.0f / RATE * HIGHFREQ * (p+1));
                                }
                            }
                            else {
                                for (int p = 0; p<SAMPLESPERBIT; p++) {
                                    uartBitEnc[p] = FloatMath.sin((float)Math.PI * 2.0f / RATE * HIGHFREQ * (p + 1));
                                }
                            }
                        } else {
                            if (nextBit == 0) {
                                for (int p = 0; p<SAMPLESPERBIT; p++) {
                                    uartBitEnc[p] = FloatMath.sin((float)Math.PI * 2.0f / RATE * LOWFREQ * (p + 1));
                                }
                            } else {
                                for (int p = 0; p<SAMPLESPERBIT; p++) {
                                    uartBitEnc[p] = -FloatMath.sin((float)Math.PI * 2.0f / RATE * LOWFREQ * (p + 1));
                                }
                            }
                        }
                        currentBit = nextBit;
                        uartBitTx++;
                        encoderState = uart_state.SAMEBIT;
                        phaseEnc = 0;
                        nextPhaseEnc = SAMPLESPERBIT;

                        break;
                    }
                    default:
                        break;
                } //end: switch(state)
                outData.put(j, (short)(uartBitEnc[phaseEnc%SAMPLESPERBIT] * outputAmplitude));
                phaseEnc++;
            } //end: for(int j = 0; j< outData.capacity(); j++)
            // copy data into left channel
            if((uartBitTx<=NUMSTOPBITS || uartSyncBitTx<=NUMSYNCBITS) && currentlySendingMessage == true) {
                // TODO not sure if need to create worker thread and render audio per http://stackoverflow.com/questions/10158409/android-audio-streaming-sine-tone-generator-odd-behaviour
                uartSyncBitTx++;
            }
            else {
                comm_sync_in_progress = false;
                // TODO Need to implement this SilenceData function
                //SilenceData(outData);
            }
        }
    }

    private void handleReceivedByte(byte myByte, boolean parityGood, long timestamp) {
        // Prepare the notification intent
    	
    	L.d(String.format("myByte coming in as %x", myByte));
    	if(myByte == (byte)0xff){
        	L.d("GOT 0xFF so not adding it to the MIX");
        	return;
        }else{
        	L.d( String.format("THE SCAN CAME IN WAS NOT 0xFF, RATHER IT WAS 0x%x" , myByte));
        }
    	
        Intent i = new Intent();
        i.setAction("com.restock.serialmagic.gears.action.SCAN");
        
        

        /*
         *  ERROR CHECKING
         */
        // Before anything else carry out error handling
        if (!parityGood) {
            // last byte was corrupted, dump this entire message
            Log.d(LOG_TAG," --- Parity Bad: dumping message.");
            markCurrentMessageCorruptAndClearBufferAtTime(timestamp);
            return;
        }
        else if (!messageValid & !(timestamp - lastByteReceivedAtTime >= MESSAGE_SYNC_TIMEOUT)) {
            // byte is ok but we're still receiving a corrupt message, dump it.
            Log.d(LOG_TAG,String.format(" --- Message Invalid: dumping message (timeout: %d)", (timestamp - lastByteReceivedAtTime)));
            markCurrentMessageCorruptAndClearBufferAtTime(timestamp);
            return;
        }
        else if (timestamp - lastByteReceivedAtTime >= MESSAGE_SYNC_TIMEOUT) {
            // sweet! timeout has passed, let's get cranking on this valid message
            if (messageReceiveBuffer.position() > 0) {
                Log.d(LOG_TAG,String.format("Timeout reached. Dumping previous buffer. \nmessageReceiveBuffer:%s \nmessageReceiveBuffer.length:%d", messageReceiveBuffer.toString(), messageReceiveBuffer.position()));
                // Prepare Intent to broadcast
                
                //YS REMOVED THIS
                i.putExtra("error",String.format("Error: message corrupt, timeout reached @ %d ms", (timestamp - lastByteReceivedAtTime)));
                sendBroadcast(i);
                
                //Toast.makeText(getApplicationContext(), "Tag scan failed. Please try again", Toast.LENGTH_LONG).show();
            }

            Log.d(LOG_TAG,String.format(" ++ Message Valid: byte is part of a new message (timeout: %d)", (timestamp - lastByteReceivedAtTime)));
            markCurrentMessageValidAtTime(timestamp);
            clearMessageBuffer();
        }

        /*
         *  BUFFER BUILDER
         */
        markCurrentMessageValidAtTime(timestamp);
        messageReceiveBuffer.put(myByte);
        messageCRC ^= myByte;

        // Have we received the message length yet?
        if (messageReceiveBuffer.position() == FLOJACK_MESSAGE_LENGTH_POSITION+FLOJACK_MESSAGE_LENGTH_LENGTH) {
            messageLength = messageReceiveBuffer.get(FLOJACK_MESSAGE_LENGTH_POSITION); // TODO: Check this is not a bug, position is 2 for length? 2's compliment?
            if (messageLength < MIN_MESSAGE_LENGTH || messageLength > MAX_MESSAGE_LENGTH)
            {
                Log.d(LOG_TAG,String.format("Invalid message length, ignoring current message."));
                markCurrentMessageCorruptAndClearBufferAtTime(timestamp);
            }
        }

        // Is the message complete?
        if (messageReceiveBuffer.position() == messageLength
                && messageReceiveBuffer.position() > MIN_MESSAGE_LENGTH) {
            // Check CRC
            if (messageCRC == CORRECT_CRC_VALUE) {
                // Well formed message received, pass it to the delegate
                Log.d(LOG_TAG,String.format("FJNFCService: Complete message, send to delegate."));
                messageReceiveBuffer.flip();
                messageReceiveBuffer.position(3);
                byte[] tmp = new byte[messageReceiveBuffer.limit()-4];
                messageReceiveBuffer.get(tmp, 0, tmp.length);
                i.putExtra("scan",String.format("%s",bytesToHex(tmp)));
                sendBroadcast(i);

                markCurrentMessageValidAtTime(timestamp);
                clearMessageBuffer();
            }
            else {
                //TODO: plumb this through to delegate
                Log.d(LOG_TAG,String.format("Bad CRC, ignoring current message."));
                //Toast.makeText(getApplicationContext(),"Tag scanning error. Please try again",Toast.LENGTH_LONG).show();
                i.putExtra("error","Tag scanning error. Please try again");
                sendBroadcast(i);
                markCurrentMessageCorruptAndClearBufferAtTime(timestamp);
            }
        }
    }

    private void clearMessageBuffer() {
        messageReceiveBuffer.clear();
        messageLength = MAX_MESSAGE_LENGTH;
        messageCRC = 0;
    }
/**
 Mark the current message corrupt and clear the receive buffer.

 @param timestamp       Time when message was marked valid and buffer cleared
 @return void
 */
    private void markCurrentMessageCorruptAndClearBufferAtTime(long timestamp) {
        markCurrentMessageCorruptAtTime(timestamp);
        clearMessageBuffer();
    }

/**
 Mark the current message invalid and timestamp.
 The message receive buffer will be flushed after transmission
 completes.

 @param timestamp       Time when message was marked corrupt
 @return void
 */
    private void markCurrentMessageCorruptAtTime(long timestamp) {
        lastByteReceivedAtTime = timestamp;
        messageValid = false;
    }

/**
 Mark the current message valid and capture the timestamp.

 @param timestamp       Time when message was marked valid
 @return void
 */
    private void markCurrentMessageValidAtTime(long timestamp) {
        lastByteReceivedAtTime = timestamp;
        messageValid = true;
    }

//    private void sendFloJackConnectedStatusToDelegate() {
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

    /**
     Get the Logic One value based on device type.

     @return byte    1 or 0 indicating logical one for this device
     */
    public byte getDeviceLogicOneValue() {
        // Get the device model number from uname
        String machineName = Build.MODEL;

        // Default value (should work on most devices)
        byte logicOneValue = 1;

        // Device exceptions
        if (machineName.equalsIgnoreCase("SCH-I925") || Build.PRODUCT.equalsIgnoreCase("gd1ltevzw") ) {
            logicOneValue = 0;
        }

        return logicOneValue;

    }

    /**
     Get the logical zero value based on device type.

     @return byte    1 or 0 indicating logical one for this device
     */

    public byte getDeviceLogicZeroValue() {
        // Return inverse of LogicOne value
        if (this.getDeviceLogicOneValue() == 1)
            return 0;
        else
            return 1;
    }

    final protected static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}

