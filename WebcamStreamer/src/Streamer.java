import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;

import javax.swing.JFrame;
import javax.swing.JToggleButton;


import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;


public class Streamer implements Runnable{
	
	private class ClientThread extends Thread{
		private Socket socket = null;

		public ClientThread(Socket socket) {
			this.socket = socket;
		}
		
		@Override
		public void run() {
			
			//System.out.println("New connection from {}" + socket.getRemoteSocketAddress());

			final BufferedReader br;
			final BufferedOutputStream bos;
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();

			try {
				br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				bos = new BufferedOutputStream(socket.getOutputStream());
			} catch (IOException e) {
				//System.out.println("Error encountering when creating I/O streams.");
				//e.printStackTrace();
				return;
			}

			try {
				while (streaming) {
					String message="HTTP/1.0 200 OK" + "\r\n" + "Connection: close" + "\r\n" + "Cache-Control: no-cache" + "\r\n" + "Cache-Control: private" + "\r\n" + "Pragma: no-cache" + "\r\n" + "Content-Type: multipart/x-mixed-replace; boundary=--mjpegframe" + "\r\n\r\n";
					bos.write(message.toString().getBytes());
					do {
						if (!webcam.isOpen() || socket.isInputShutdown() || socket.isClosed()) {
							br.close();

							return;
						}

						baos.reset();
						
						image = webcam.getImage();
						ImageIO.write(image, "JPG", baos);
		
						String serverresponse = "--mjpegframe" + "\r\n" + "Content-type: image/jpeg" + "\r\n" + "Content-Length: " + baos.size() + "\r\n" + "\r\n";

						try {
							bos.write(serverresponse.getBytes());
							bos.write(baos.toByteArray());
							bos.write("\r\n".getBytes());
							bos.flush();
						} catch (SocketException e) {
							
							if (!socket.isConnected()) {
								System.out.println("Connection to client has been lost");
							}
							if (socket.isClosed()) {
								System.out.println("Connection to client is closed");
							}

							return;
						}

						Thread.sleep(10);

					} while (streaming);
				}
			} catch (Exception e) {
				String message = e.getMessage();
				if (message != null) {
					if (message.startsWith("Software caused connection abort")) {
						System.out.println("User closed stream.");
						return;
					}
					if (message.startsWith("Broken pipe")) {
						System.out.println("User connection broken.");
						return;
					}
				}
				e.printStackTrace();

				try {
					bos.write("HTTP/1.0 501 Internal Server Error\r\n\r\n\r\n".getBytes());
				} catch (IOException e1) {
					System.out.println("Not able to write to output stream.");
				}

			} finally {
				//System.out.println("Closing connection from " + socket.getRemoteSocketAddress());
					
				for (Closeable closeable : new Closeable[] { br, bos, baos }) {
					try {
						closeable.close();
					} catch (IOException e) {
						//System.out.println("Cannot close I/O stream");
					}
				}
				try {
					socket.close();
				}catch(IOException e) {
					System.out.println("Cannot close the socket.");
				}

			}
		}
	
	}
	
	
	private Webcam webcam = null;
	private BufferedImage image = null;
	private static boolean streaming = false;
	private static ServerSocket server = null;
	private static ArrayList<ClientThread> clientList = new ArrayList<ClientThread>();

	
	
	public Streamer(Webcam webcam) {

		if (webcam == null) {
			throw new IllegalArgumentException("Webcam for streaming cannot be null");
		}

		this.webcam = webcam;
	}
	
	public static void establishServer(int port,Streamer streamer){
		try{
			server = new ServerSocket(port);
			System.out.println("Server has been established.");
			while(true){
					Socket socket = null;
					try {
						socket = server.accept();
					} catch (IOException e1) {
						//e1.printStackTrace();
					}
					
					ClientThread client = streamer.new ClientThread(socket);
					clientList.add(client);
					client.start();
			}
		
		}catch (Exception e){
			System.out.println("Error creating server");
			System.out.println(e.getMessage());
			System.exit(-1);
		}
	}

	
	public static void removeClient() {
		for (int j = 0;j<clientList.size();j++){
			clientList.remove(j);
		}
	}
	
	
	public void startStreaming() {
		streaming = true;
	}
	public void stopStreaming() {
		streaming = false;
	}
	@Override
	public void run() {
		establishServer(8080,this);
	}
	
	private static void Window() {
		JFrame window = new JFrame("Webcam");
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setBounds(100, 100, 450, 300);
		window.setResizable(true);
		
		Webcam webcam = Webcam.getDefault();
		webcam.setViewSize(WebcamResolution.VGA.getSize());
	
		WebcamPanel panel1 = new WebcamPanel(webcam);
		panel1.setFPSDisplayed(true);
		panel1.setDisplayDebugInfo(false);
		panel1.setImageSizeDisplayed(true);
		panel1.setMirrored(true);
		window.add(panel1);
		Streamer streamer = new Streamer(webcam);
		Thread t = new Thread(streamer);
		t.start();
		
		
		
		
		String[] rslt = { "QQVGA(176, 144)", "QVGA(320, 240)", "VGA(640, 480)"};
		
		JComboBox<String> resolution = new JComboBox<String>(rslt);
		resolution.setSelectedIndex(2);
		resolution.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String s = (String) resolution.getSelectedItem();
				WebcamPanel previouspanel = panel1;
				switch (s) {//check for a match
                case "QQVGA(176, 144)":
                    System.out.println("QQVGA selected");
                    previouspanel = initialize(webcam,WebcamResolution.QQVGA,window,previouspanel);
                    break;
                case "QVGA(320, 240)":
                    System.out.println("QVGA selected");
                    previouspanel = initialize(webcam,WebcamResolution.QVGA,window,previouspanel);
                    break;
                case "VGA(640, 480)":
                    System.out.println("VGA selected");
                    previouspanel = initialize(webcam,WebcamResolution.VGA,window,previouspanel);
                    break;
                default:
                    System.out.println("No resolution selected");
                    previouspanel = initialize(webcam,WebcamResolution.VGA,window,previouspanel);
                    break;
				}
			}
		});
		window.add(resolution, BorderLayout.SOUTH);		
		
		
		
		
		JToggleButton streaming = new JToggleButton("Streaming..");
		streaming.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
			
				if (streaming.isSelected()) {
						System.out.println("Start streaming.");	
						streamer.startStreaming();
						
					}
				else {
						System.out.println("Stop streaming.");
						streamer.stopStreaming();
						streamer.removeClient();						
					}
				 }
		});
		window.add(streaming, BorderLayout.WEST);
		
		window.setVisible(true);
	}

	public static WebcamPanel initialize(Webcam webcam, WebcamResolution size, JFrame window,WebcamPanel previousPanel) {
		if(webcam.isOpen()) {
			webcam.close();
		}
		
		
		webcam = Webcam.getDefault();
		webcam.setViewSize(size.getSize());
		
		previousPanel.setVisible(false);
		window.getContentPane().remove(previousPanel);
		WebcamPanel panel = new WebcamPanel(webcam);

		panel.setFPSDisplayed(true);
		panel.setDisplayDebugInfo(false);
		panel.setImageSizeDisplayed(true);
		panel.setMirrored(true);
		window.add(panel);
		window.validate(); // Changed here
        window.repaint(); // Changed here

		return panel;
	}
	public static void main(String args[]) {
		Window();
	}
}