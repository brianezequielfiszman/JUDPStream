package udp.client;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

public class Client {

	private ByteArrayOutputStream byteOutputStream;
	private AudioFormat adFormat;
	private TargetDataLine targetDataLine;
	private Object promptLock = new Object();
	private Object streamLock = new Object();

	private boolean captureEnabled;
	private boolean quitSelected;

	public void captureAudio() {
		try {
			adFormat = getAudioFormat();
			DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, adFormat);
			targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
			targetDataLine.open(adFormat);
			targetDataLine.start();

			Thread captureThread = new Thread(new CaptureThread());
			Thread promptThread = new Thread(new PrompThread());

			promptThread.start();

			synchronized (promptLock) {
				promptLock.wait();
			}

			captureThread.start();

		} catch (Exception e) {
			StackTraceElement stackEle[] = e.getStackTrace();
			for (StackTraceElement val : stackEle) {
				System.out.println(val);
			}
			System.exit(0);
		}
	}

	private AudioFormat getAudioFormat() {
		float sampleRate = 44100.0F;
		int sampleInbits = 16;
		int channels = 2;
		boolean signed = true;
		boolean bigEndian = false;
		return new AudioFormat(sampleRate, sampleInbits, channels, signed, bigEndian);
	}

	class CaptureThread extends Thread {
		private byte tempBuffer[] = new byte[Integer.BYTES * 4096];
		private DatagramSocket clientSocket;

		@Override
		public void run() {

			byteOutputStream = new ByteArrayOutputStream();
			try {
				clientSocket = new DatagramSocket();
				InetAddress IPAddress = InetAddress.getByName("192.168.1.148");
				while (!quitSelected) {
					while (captureEnabled) {
						int cnt = targetDataLine.read(tempBuffer, 0, tempBuffer.length);
						if (cnt > 0) {
							DatagramPacket sendPacket = new DatagramPacket(tempBuffer, tempBuffer.length, IPAddress,
									5555);
							clientSocket.send(sendPacket);
							byteOutputStream.write(tempBuffer, 0, cnt);
						}
					}
					synchronized (streamLock) {
						streamLock.wait();
					}
				}
				byteOutputStream.close();
			} catch (Exception e) {
				System.out.println("CaptureThread::run()" + e);
				System.exit(0);
			}
		}
	}

	class PrompThread extends Thread {
		private Scanner s = new Scanner(System.in);
		private boolean started = false;

		@Override
		public void run() {
			while (!quitSelected)
				if (!started) {
					started = true;
					captureEnabled = this.prompt();
					synchronized (promptLock) {
						promptLock.notifyAll();
					}
				} else {
					captureEnabled = this.prompt();
					synchronized (streamLock) {
						streamLock.notifyAll();
					}
				}
		}

		private boolean prompt() {
			System.out.println("Hello User\n" + "Enter an option\n" + "1. Start!\n" + "2. Stop.\n" + "3. Quit\n");

			int opt = s.nextInt();
			return (opt == 1) ? true : (opt == 2) ? false : (opt == 3) ? !(quitSelected = true) : this.prompt();
		}

	}
}