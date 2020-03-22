package net.speedstor.outdated;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.Icon;

import org.json.simple.JSONObject;

import net.speedstor.main.DiscussionHandler;
import net.speedstor.main.Log;
import net.speedstor.main.Server;

public class ServerThread extends Thread{
	Socket client;
	Log log;
	Server server;
	
	public ServerThread(Socket client, Log log, Server server) {
		this.client = client;
		this.log = log;
		this.server = server;
	}
	
	public void run() {
		try {
	        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
			PrintWriter headerOut = new PrintWriter(client.getOutputStream());
	        BufferedOutputStream outToClient = new BufferedOutputStream(client.getOutputStream());
	        
	        String clientRequest = inFromClient.readLine();
	        
	        HashMap<String, String> requestHeader = new HashMap<String, String>();
	        String requestHeaderString = "";
	        requestHeader.put("loc", clientRequest);
	        String line1;
	        int backup = 0;
	        while ((line1 = inFromClient.readLine()) != null) {
	            requestHeaderString += line1;
	        	if (!line1.isEmpty()) {
		        	int colonLoc = line1.indexOf(":");
		        	if(colonLoc > 0) {
		        		requestHeader.put(line1.substring(0, colonLoc), line1.substring(colonLoc+2));
		        	}else {
		        		requestHeader.put(""+backup, line1);
		        		backup++;
		        	}
	            }
	        }
	        
	        String[] request;
	        
	        if(clientRequest != null) {
	        	log.log("Request: "+client+"; Path: "+clientRequest);
	        	request = clientRequest.split(" ");
	        
		        String returnClientRequest = "";
		        String contentType = "text/plain";
		        byte[] returnByte;
		        
		        //create response
		        int urlSeperatorLoc = request[1].indexOf("?");
		        String requestedPath;
		        if(urlSeperatorLoc > 0) {
		        	requestedPath = request[1].substring(0, urlSeperatorLoc);
		        }else {
		        	requestedPath = request[1];
		        }
		        switch(requestedPath) {
		        case "/initDiscu":
		        	returnClientRequest = loginUser(request[1].substring(urlSeperatorLoc + 1));
		        	break;
		        case "/socket":
		        	int success = upgradeToWebSocket(request[1].substring(urlSeperatorLoc + 1), requestHeader, requestHeaderString, inFromClient, headerOut, outToClient);
		        	if(success == 1) {
		        		log.log("Web Socket Established");
		        	}else {
		        		log.error("Web Socket Failed");
		        	}
		        	break;
				case "/favicon.ico":
		        	//doesn't work yet
		        	try {
		        		File file = new File(getClass().getResource("/resource/favicon.ico").getFile());
		        	
						int fileLength = (int) file.length();
						
						FileInputStream fileInputStream = null;
						byte[] fileData = new byte[fileLength];
						
						try {
							fileInputStream = new FileInputStream(file);
							fileInputStream.read(fileData);
						} finally {
							if (fileInputStream != null) 
								fileInputStream.close();
						}
						
						
						contentType = "image/vnd";
						//contentType = "text/plain";
						returnByte = fileData;
					}catch (IOException e){
			        	returnClientRequest = "error";
			        	log.error("Requested for favicon.ico, still didn't implement");		        		
		        	}
					break;
		        case "":
		        case "/":
		        	returnClientRequest = "this canvas server for live discussion";
		        	break;
		        default:
					returnClientRequest = "404 - Not Found";
					break;
		        }
		        
		        if(returnClientRequest != "") {
		        	returnByte = returnClientRequest.getBytes();
		        }else {
		        	returnByte = new byte[0];
		        }
		        
				// send HTTP Headers
				headerOut.println("HTTP/1.1 200 OK");
				headerOut.println("Server: Java HTTP Server from SSaurel : 1.0");
				headerOut.println("Date: " + new Date());
				headerOut.println("Content-type: " + contentType);
				headerOut.println("Content-length: " + returnByte.length);
				headerOut.println(); // blank line between headers and content, very important !
				headerOut.flush(); // flush character output stream buffer
		        
				outToClient.write(returnByte, 0, returnByte.length);
				outToClient.flush();
	
				
		        
		        
		        StringBuilder stringBuilder = new StringBuilder();
		        String line;
		        while( (line = inFromClient.readLine()) != null) {
		        	stringBuilder.append(line+"\n");
		        }
		        String fullRequestHeader = stringBuilder.toString();
	        }
	        
	        
			//client.close();
			
		}catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private int upgradeToWebSocket(String paramters, HashMap<String, String> clientRequest, String requestHeaderString, BufferedReader inFromClient, PrintWriter headerOut, BufferedOutputStream outToClient) {

		try {
			
			
			log.log(clientRequest.toString());
			String socketKey = clientRequest.get("Sec-WebSocket-Key");
			
			if(server.clientConnections.containsKey(client.getInetAddress().toString())) {
				//add one to the count
				int value = server.clientConnections.get(client.getInetAddress().toString());
				
				if(value > 17) {
					//return 0;
				}
				server.clientConnections.replace(client.getInetAddress().toString(), value++);
			}else {
				server.clientConnections.put(client.getInetAddress().toString(), 1);
			}

			InputStream in = client.getInputStream();
			OutputStream out = client.getOutputStream();
			
		
				log.special(socketKey);
				log.special("Sec-WebSocket-Accept: "+ Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((socketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8"))));
				
				headerOut.println("HTTP/1.1 101 Switching Protocols");
				headerOut.println("Connection: Upgrade");
				headerOut.println("Upgrade: WebSocket");
				headerOut.println("Sec-WebSocket-Accept: "+ Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((socketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8"))));

				headerOut.println(); // blank line between headers and content, very important !
				log.special(""+headerOut.checkError()); // flush character output stream buffer

				
		        //int dataBinary = Integer.parseInt("100000100000100001000001", 2);
		        //ByteBuffer bytes = ByteBuffer.allocate(2).putInt(dataBinary);
		        
		        String binaryString = "100000100000100001000001";

		        byte[] returnByte =  new BigInteger(binaryString, 2).toByteArray();
		        
		        returnByte = "123sdf".getBytes();
		        
		        
				outToClient.write(returnByte, 0, returnByte.length);
				//outToClient.flush();
				
				byte[] decoded = new byte[6];
				byte[] encoded = new byte[] { (byte) 198, (byte) 131, (byte) 130, (byte) 182, (byte) 194, (byte) 135 };
				byte[] key = new byte[] { (byte) 167, (byte) 225, (byte) 225, (byte) 210 };
				for (int i = 0; i < encoded.length; i++) {
					decoded[i] = (byte) (encoded[i] ^ key[i & 0x3]);
				}
				
				
				String finalResponse = new String(decoded);
				log.log("WebSocket Response: "+finalResponse);
			//}

		} catch (IOException | NoSuchAlgorithmException e) {
			e.printStackTrace();
			log.error("writing websocket header error");
		}
		return 1;
	}

	private String loginUser(String parametersString) {
		String[] parametersArray = parametersString.split("&");
		JSONObject parameters = new JSONObject();
		for(int i = 0; i < parametersArray.length; i++) {
			int equalLoc = parametersArray[i].indexOf("=");
			if(equalLoc > 0) {
				String key = parametersArray[i].substring(0, equalLoc);
				String value = parametersArray[i].substring(equalLoc + 1);
				
				parameters.put(key, value);
			}else {
				return "-1 - parameter error";
			}
		}
		
		if(!parameters.containsKey("token")) {
			return "-1 - must need token";
		}
		
		if(!parameters.containsKey("url")) {
			return "-1 - must need url";
		}
		
		String canvasToken = (String) parameters.get("token");
		String testApiFetch = sendGet("https://fairmontschools.instructure.com/api/v1/courses?access_token="+canvasToken);
		String errorResponseString = "error: catch error";
		if(testApiFetch == errorResponseString) {
			log.warn("Invalid login attempt");
			return "-1 - invalid token";
		}
		
		//token for communication between server and client, not using canvas token
		int tokenLength = 15; //do not change !!!
		String token;

		if(server.tokens.toJSONString().contains(canvasToken)) {
			
			int tokenLoc = server.tokens.toJSONString().indexOf(canvasToken);
			token = server.tokens.toJSONString().substring(tokenLoc - 3 - tokenLength, tokenLoc - 3);
		}else {
			
			token = getAlphaNumericString(tokenLength);
			
			//make sure no repeat
			while(server.tokens.containsKey(token)) {
				token = getAlphaNumericString(tokenLength);				
			}
			
			//write in file: serverToken - CanvasToken
			
			try {
				File f = new File("../canvasBot");
				if(!f.exists()) System.out.println("File Storage does not exsist --Creating storage folder: "+(new File("../canvasBot")).mkdirs());
				
				File file = new File("../canvasBot", "tokens.txt");
				FileWriter fr = new FileWriter(file, true);
				fr.write("\n"+token+":"+canvasToken);
				fr.close();
				
				log.log("New User: " + token);
				
			}catch(IOException e){
				return "server writing error";
			}
			
			server.tokens.put(token, canvasToken);
		}
		

		String discussionUrl = (String) parameters.get("url");
		if(discussionUrl.contains("?")) {
			discussionUrl = discussionUrl.substring(0, discussionUrl.indexOf("?"));
		}
		
		if(discussionUrl.contains("discussion_topics") && discussionUrl.contains("fairmontschools.instructure.com/courses/")) {
			discussionUrl = "https://fairmontschools.instructure.com/api/v1"+discussionUrl.substring(discussionUrl.indexOf("/courses/"))+"/view";
			
			String testDiscussionResponse = sendGet(discussionUrl+"?include_new_entries=1&include_enrollment_state=1&include_context_card_info=1&access_token="+canvasToken);
			if(testDiscussionResponse == "error: catch error") {
				token += "-0";
			}
			
			if(server.runningDiscussionBoards.containsKey(discussionUrl)) {
				
				//server.runningDiscussionBoards.get(discussionUrl).addParticipant(token);
				
			}else {
				DiscussionHandler discussionHandler = new DiscussionHandler(log, discussionUrl);
				//discussionHandler.addParticipant(token);
				Thread discussionHandlerThread = new Thread(discussionHandler);
				discussionHandlerThread.start();
				
				
				server.runningDiscussionBoards.put(discussionUrl, discussionHandler);
				
				log.log("New DBoard: " + discussionHandler.toString() + "; Total Boards: " + server.runningDiscussionBoards.size() + "; url: " + discussionUrl.substring(40, discussionUrl.indexOf("?")));
			}
		}else {
			token = token + "-0"; //needs to be a discussion topic
		}
		
		
		return token;
	}
	

	// from  https://www.geeksforgeeks.org/generate-random-string-of-given-size-in-java/
    // function to generate a random string of length n 
    private static String getAlphaNumericString(int n) { 
  
        // chose a Character random from this String 
        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                                    + "0123456789"
                                    + "abcdefghijklmnopqrstuvxyz"; 
  
        // create StringBuffer size of AlphaNumericString 
        StringBuilder sb = new StringBuilder(n); 
  
        for (int i = 0; i < n; i++) { 
  
            // generate a random number between 
            // 0 to AlphaNumericString variable length 
            int index 
                = (int)(AlphaNumericString.length() 
                        * Math.random()); 
  
            // add Character one by one in end of sb 
            sb.append(AlphaNumericString 
                          .charAt(index)); 
        } 
  
        return sb.toString(); 
    } 
	
    
    
	public String sendGet(String url) {
		try {
			URL urlObj = new URL(url);
			HttpURLConnection con = (HttpURLConnection) urlObj.openConnection();
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", "Mozilla/5.0");
			int responseCode = con.getResponseCode();
			//if (responseCode == HttpURLConnection.HTTP_OK) {
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();
	
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();
	
				// print result
				return response.toString();
			//} else {
			//	log.error("GET request not worked");
			//	return "error: get response code error";
			//}
		}catch(IOException e){
			//e.printStackTrace();
			return "error: catch error";
		}
	}
}
