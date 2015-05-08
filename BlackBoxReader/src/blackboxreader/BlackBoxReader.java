/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package blackboxreader;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.net.ConnectException;
import java.net.Socket;
import java.io.*;
import java.util.*;
import java.awt.event.ActionEvent;
import java.net.SocketException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;


/**
 *
 * @author Johnny White
 */
public class BlackBoxReader extends JFrame {
    //CANBus Message IDs
    static final int ID_OFFSET = 21;
    static final int ID_SCALE = 1 << ID_OFFSET;
    static final int TIME_MESSAGE_ID = 256 * ID_SCALE;              // 0x200
    static final int MOTOR_MESSAGE_ID = 296 * ID_SCALE;             // 0x250
    static final int TORQUE_COMMAND_MESSAGE_ID = 300 * ID_SCALE;    // 0x258
    static final int STATE_MESSAGE_ID = 304 * ID_SCALE;             // 0x260
    static final int PARAM_REQUEST_MESSAGE_ID = 312 * ID_SCALE;     // 0x270 
    static final int LAUNCH_PARAM_MESSAGE_ID = 327 * ID_SCALE;      // 0x28e
    static final int CP_CL_RMT_MESSAGE_ID = 328 * ID_SCALE;         // 0x290
    static final int CP_CL_LCL_MESSAGE_ID = 329 * ID_SCALE;         // 0x292
    static final int CP_INPUTS_RMT_MESSAGE_ID = 330 * ID_SCALE;     // 0x294
    static final int CP_INPUTS_LCL_MESSAGE_ID = 331 * ID_SCALE;     // 0x296
    static final int CP_OUTPUTS_MESSAGE_ID = 336 * ID_SCALE;        // 0x2A0
    static final int ORIENTATION_ID = 385 * ID_SCALE;               // 0x302
    static final int DRUM_MESSAGE_ID = 432 * ID_SCALE;              // 0x360
    static final int TENSION_MESSAGE_ID = 448 * ID_SCALE;           // 0x380
    static final int CABLE_ANGLE_MESSAGE_ID = 464 * ID_SCALE;       // 0x3a0
    static final int DENSITY_ALTITUDE_ID = 689 * ID_SCALE;          // 0x562
    static final int WIND_ID = 690 * ID_SCALE;                      // 0x564
    static final int BATTERY_SYSTEM_ID = 704 * ID_SCALE;            // 0x580
///////////////////////////////////////////////////////////////////////////////
    static final String HUBSERVER_ADDRESS = "147.222.165.75";       //HUB-SERVER
    static final int HUBSERVER_PORT = 32123;
////////////////////////////////////////////////////////////////////////////////
    static JPanel display;
    
    
    
    public static void main(String[] args) throws InterruptedException, IOException, ParseException{
        String time_stamp                   = "";
        String  message                     = "";
        String  message_type                = "";
        String  file_name                   = "";
        File file                           = null;
        int returnVal                       = 0;
        display                             = new JPanel();
        SimpleDateFormat sdf                = new SimpleDateFormat("MM dd yyyy hh:mm:ss");
        FileWriter writer;

        String newFileName = "BlackBoxReport.csv";
        File newFile = new File(newFileName);
        FilterUI filter = new FilterUI();
        filter.setVisible(true);
        //Calendar cal = Calendar.getInstance();
        
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open");
        chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
//        FileNameExtensionFilter filter = new FileNameExtensionFilter("csv");
//        chooser.setFileFilter(filter);
        returnVal = chooser.showOpenDialog(display);
        if (returnVal == JFileChooser.APPROVE_OPTION){
            file = chooser.getSelectedFile();
            file_name = chooser.getName();
            
        }
        
        
////////////Get first and last time statements for UI/////////////////////////////
//        BufferedReader getter   = new BufferedReader(new FileReader(file));
//        String currentLine      = getter.readLine();
//        String[] entryTime      = currentLine.split(",");
//        String firstTime        = entryTime[0];
//        String lastTime         = "";
//        while((currentLine = getter.readLine()) != null){
//            entryTime = currentLine.split(",");
//            lastTime = entryTime[0];
//        }
//        Calendar firstTimeCal   = Calendar.getInstance();
//        Calendar lastTimeCal    = Calendar.getInstance();
//        Date firstDate          = new Date();
//        Date lastDate           = new Date();
//        firstTimeCal = unixToCalendar(1000 * (long) Float.parseFloat(firstTime)); //String -> Long -> Calendar
//        lastTimeCal = unixToCalendar(1000 * (long) Float.parseFloat(lastTime)); //String -> Long -> Calendar
//
//        
//        firstDate = firstTimeCal.getTime();                             //Calendar -> Date 
//        filter.firstLoggerEntryTime.setText(sdf.format(firstDate));     //Date -> String
//        lastDate = lastTimeCal.getTime();                             //Calendar -> Date 
//        filter.lastLoggerEntryTime.setText(sdf.format(lastDate));     //Date -> String
//////////////////////////////////////////////////////////////////////////////////        
        
        //Read from the Blackbox
        newFile = filterBlackBox(file, filter);
        writer = new FileWriter(newFile);
        try (BufferedReader reader = new BufferedReader(new FileReader(newFile))) {
                String line = "";
                while((line = reader.readLine()) != null){
                    String[] entry = line.split(",");
                    time_stamp = messageTime(entry[0]);
                    message    = messageParser(entry[1]);
                    writer.append(time_stamp + ", " + message + "\n");
                    System.out.println(time_stamp + ", " + message);
                }
                writer.flush();
                writer.close();
//                //Save File File-Chooser
//                JFileChooser saveFile = new JFileChooser();
//                saveFile.setDialogTitle("Save");
//                int path = saveFile.showSaveDialog(null);
//                if (path == JFileChooser.APPROVE_OPTION){
//                    
//                }
        }
        catch (IOException ioe){
            ioe.printStackTrace();
        }
    }
    
    public static File filterBlackBox(File file, FilterUI filter) throws IOException, ParseException{
        FileWriter writer = new FileWriter(file);
        //Time Stamp Information
        SimpleDateFormat sdf            = new SimpleDateFormat("dd:MM:yyyy hh:mm:ss");
        Calendar startDate;                 
        Calendar endDate;               // Year Month and Day
        //Message ID Information
        String messageID                = "";
        float cableDeployed             = 0;
        //Flags for checkboxes: default is false
        boolean messageFlag             = false;
        boolean timeFlag                = false;
        //Drum Message exception: print all unless otherwise stated
        boolean drumFlag                = true;
        //Drum and Motor Systems
        File returnFile                 = file;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line     = "";
            long unixTime   = 0;
            String time     = "";
            String message  = "";
            while((line = reader.readLine()) != null){
                String[] entry  = line.split(",");
                unixTime        = Long.parseLong(entry[0]);
                time            = messageTime(entry[0]);
                timeFlag        = checkTimes(time, filter);
                message         = (entry[1]);
                messageFlag     = checkCheckBox(message, filter);
                if(timeFlag && messageFlag && drumFlag){
                    writer.append(time + ", " + message + "\n");
                    System.out.println(time + ", " + message);
                }
            }
            writer.flush();
            writer.close();
        }
        

        return returnFile;
    }
    
public static boolean checkTimes(String time, FilterUI filter) throws ParseException{
    boolean flag             = false;
    long unixTime            = Long.parseLong(time);
    SimpleDateFormat sdf     = new SimpleDateFormat("MM dd yyyy hh:mm:ss");
    Calendar startDate       = Calendar.getInstance();
    Calendar endDate         = Calendar.getInstance();
    Calendar startDateWanted = Calendar.getInstance();
    Calendar endDateWanted   = Calendar.getInstance();
    Calendar currentTime     = Calendar.getInstance();
    startDateWanted.setTime(sdf.parse(filter.firstLoggerEntryTime.getText())); //TextField -> String -> Date -> Calendar
    endDateWanted.setTime(sdf.parse(filter.lastLoggerEntryTime.getText())); //TextField -> String -> Date -> Calendar
    currentTime = unixToCalendar(unixTime);
    //if c1.compareTo(c2) > 0 -> c2 is a later time
    //if c1.compareTo(c2) < 0 -> c2 is an earlier time
    if((startDateWanted.compareTo(currentTime) >= 0) && (endDateWanted.compareTo(currentTime) <= 0)){
        flag = true;
    }
    return flag;
}
public static boolean checkCheckBox(String messageID, FilterUI filter){
    boolean flag = false;
    CanCnvt canIn = new CanCnvt();
    canIn.convert_msgtobin(messageID);
    switch (canIn.id){
        case TIME_MESSAGE_ID:
            if (filter.timeMsg.isSelected()){
                flag = true;
            }
            break;
        case MOTOR_MESSAGE_ID://status, rps, revolutions, temperature
            if (filter.motorMessage.isSelected()){
                flag = true;
            }
            break;
        case TORQUE_COMMAND_MESSAGE_ID://status, motorTorque, lastCommandedDrumTorque
            if (filter.torqueMsg.isSelected()){
                flag = true;
            }
            break;
        case STATE_MESSAGE_ID: //("State and Active Drum: " + currentState + "  " +  activeDrum);
            if (filter.stateMsg.isSelected()){
                flag = true;
            }
            break;
        case PARAM_REQUEST_MESSAGE_ID://status
            if (filter.parameterMsg.isSelected()){
                flag = true;
            }
            break;
        case LAUNCH_PARAM_MESSAGE_ID://Parameter_Value, Parameter_Index
            if (filter.launchMsg.isSelected()){
                flag = true;
            }
            break;
        case CP_CL_RMT_MESSAGE_ID://status, unfiltered position, filtered position
            if (filter.rmtLeverMsg.isSelected()){
                flag = true;
            }
            break;
        case CP_CL_LCL_MESSAGE_ID://status, unfiltered position, filtered posiition
            if (filter.lclLeverMsg.isSelected()){
                flag = true;
            }
            break;
        case CP_INPUTS_RMT_MESSAGE_ID://spi_inputs
            if (filter.rmtPanelMsg.isSelected()){
                flag = true;
            }
            break;
        case CP_INPUTS_LCL_MESSAGE_ID://spi_inputs
            if (filter.lclPanelMsg.isSelected()){
                flag = true;
            }
            break;
        case CP_OUTPUTS_MESSAGE_ID://spi_outputs, beeper_count
            if (filter.controlPanelOutput.isSelected()){
                flag = true;
            }
            break;
        case ORIENTATION_ID:// System.out.println("Orientation: " + pitch + " " +  roll + " " + magnetic);
            if (filter.orientation.isSelected()){
                flag = true;
            }
            break;
        case DRUM_MESSAGE_ID://last_cable_out, last_cable_speed
            if (filter.drumMsg.isSelected()){
                flag = true;
            }
            break;
        case TENSION_MESSAGE_ID://status, activeDrum, lastCableTension
            if (filter.tensionMsg.isSelected()){
                flag = true;
            }
            break;
        case CABLE_ANGLE_MESSAGE_ID://status, activeDrum, lastCableTension
            if (filter.cableAngleMsg.isSelected()){
                flag = true;
            }
            break;
        case DENSITY_ALTITUDE_ID://System.out.println("Density Altitude: " + pressure + " " +  temperature + " " + hummidity);
            if (filter.densityAltitude.isSelected()){
                flag = true;
            }
            break;
        case WIND_ID://System.out.println("Wind: " + direction + " " +  speed + " " + gust);
            if (filter.wind.isSelected()){
                flag = true;
            }
            break;
        case BATTERY_SYSTEM_ID://System.out.println("Battery System: " + voltage + " " +  current + " " + temperature);
            if (filter.timeMsg.isSelected()){
                flag = true;
            }
            break;
    }
    return flag;
}
    
    
public static Calendar unixToCalendar(long unixTime){
    SimpleDateFormat sdf    = new SimpleDateFormat("MM dd yyyy hh:mm:ss");
    Calendar calendar       = Calendar.getInstance();
    Date date               = new Date(unixTime);
    calendar.setTime(date);
    return calendar;
}
    
    public static String messageTime(String msg){
        CanCnvt canIn = new CanCnvt();
        canIn.convert_msgtobin(msg);
        int intUnixTime = canIn.get_int(0);
        short status = canIn.get_ubyte(4);
        return Integer.toString(intUnixTime);
    }
    

    
    public static String messageParser(String msg){
        CanCnvt canIn = new CanCnvt();
        canIn.convert_msgtobin(msg);
        short status;
        String message = "";
        String type = "";
        switch (canIn.id){
            case TIME_MESSAGE_ID://Status, Unix Time
                int unix_time = canIn.get_int(0);
                status = canIn.get_short(4);
                type = "Time Message";
                message = "Status: " + Short.toString(status) + ", " +
                        "Time: " + Integer.toString(unix_time)+ ", "
                        + "Message Value: " + Integer.toString(TIME_MESSAGE_ID / ID_SCALE);
                break;
            case MOTOR_MESSAGE_ID://status, rps, revolutions, temperature
                type = "Motor Message";
                float rps = canIn.get_halffloat(0);
                float revolutions = canIn.get_halffloat(2);
                float temperature0 = (float) canIn.get_byte(4);
                status = canIn.get_byte(5);
                message = "Status: " + Short.toString(status) + ", " + "Speed: " 
                        + Float.toString(rps) + ", " + "Revolutions: " + 
                        Float.toString(revolutions) + ", " + "Temperature: " + 
                        Float.toString(temperature0)  + ", "
                        + "Message Value: " + Integer.toString(MOTOR_MESSAGE_ID / ID_SCALE);
                break;
            case TORQUE_COMMAND_MESSAGE_ID://status, motorTorque, lastCommandedDrumTorque
                type = "Torque Command Message";
                float maxTorque = canIn.get_halffloat(0);
                float maxCableSpeed = canIn.get_halffloat(2);
                message = "Commanded/Maximum Torque" + Float.toString(maxTorque)
                        + ", " + "Commanded/Maximum Cable Speed: " + 
                        Float.toString(maxCableSpeed)  + ", "
                        + "Message Value: " + Integer.toString(TORQUE_COMMAND_MESSAGE_ID / ID_SCALE);
                break;
            case STATE_MESSAGE_ID: //("State and Active Drum: " + currentState + "  " +  activeDrum);
                type = "State Message";
                float super_state = canIn.get_halffloat(0);
                float sub_state = canIn.get_halffloat(1);
                message = "Super-State: " + Float.toString(super_state) + ", " + 
                        "Sub-State: " + Float.toString(sub_state)  + ", "
                        + "Message Value: " + Integer.toString(STATE_MESSAGE_ID / ID_SCALE);
                break;
            case PARAM_REQUEST_MESSAGE_ID://status
                type = "Parameter Request";
                message = "Parameter Request: Sent"  + ", "
                        + "Message Value: " + Integer.toString(PARAM_REQUEST_MESSAGE_ID / ID_SCALE);
                break;
            case LAUNCH_PARAM_MESSAGE_ID://Parameter_Value, Parameter_Index
                type = "Launch Parameter Message";
                float value = canIn.get_halffloat(0);
                int index = canIn.get_int(5);
                message = "Parameter Index: " + Integer.toString(index) + ", " + 
                        "Parameter Value: " + Float.toString(value) + ", "
                        + "Message Value: " + Integer.toString(LAUNCH_PARAM_MESSAGE_ID / ID_SCALE);
                break;
            case CP_CL_RMT_MESSAGE_ID://status, unfiltered position, filtered position
                type = "Control Panel Control Lever Remote Message";
                float unfiltered_position = canIn.get_halffloat(0);
                status = canIn.get_byte(2);
                message = "Status: " + Short.toString(status) + ", " + "Position"
                        + Float.toString(unfiltered_position)  + ", "
                        + "Message Value: " + Integer.toString(CP_CL_RMT_MESSAGE_ID / ID_SCALE);
                break;
            case CP_CL_LCL_MESSAGE_ID://status, unfiltered position, filtered posiition
                type = "Control Panel Control Lever Message";
                float unfiltered_position_lcl = canIn.get_halffloat(0);
                status = canIn.get_byte(2);
                message = "Status: " + Short.toString(status) + ", " + "Position"
                        + Float.toString(unfiltered_position_lcl)  + ", "
                        + "Message Value: " + Integer.toString(CP_CL_LCL_MESSAGE_ID / ID_SCALE);
                break;
            case CP_INPUTS_RMT_MESSAGE_ID://spi_inputs
                type = "Control Panel Inputs Remote Message";
                float spi_inputs = canIn.get_halffloat(0);
                message = "SPI Input: " + Float.toString(spi_inputs) + ", "
                        + "Message Value: " + Integer.toString(CP_INPUTS_RMT_MESSAGE_ID / ID_SCALE);
                break;
            case CP_INPUTS_LCL_MESSAGE_ID://spi_inputs
                type = "Control Panel Inputs Control Lever Message";
                float spi_inputs_rmt = canIn.get_halffloat(0);
                message = "SPI Input: " + Float.toString(spi_inputs_rmt)  + ", "
                        + "Message Value: " + Integer.toString(CP_INPUTS_LCL_MESSAGE_ID / ID_SCALE);
                break;
            case CP_OUTPUTS_MESSAGE_ID://spi_outputs, beeper_count
                type = "Control Panel Outputs Request" + ", "
                        + "Message Value: " + Integer.toString(CP_OUTPUTS_MESSAGE_ID / ID_SCALE);
                
                break;
            case ORIENTATION_ID:// System.out.println("Orientation: " + pitch + " " +  roll + " " + magnetic);
                type = "Orientation Message";
                float pitch = canIn.get_halffloat(0);
                float roll = canIn.get_halffloat(2);
                float magnetic = canIn.get_halffloat(4);
                status = canIn.get_byte(2);
                message = "Status: " + Short.toString(status) + ", " + "Pitch: " +
                        Float.toString(pitch) + ", " + "Roll: " + 
                        Float.toString(roll) + ", " + "Magnetic: " + 
                        Float.toString(magnetic)  + ", "
                        + "Message Value: " + Integer.toString(ORIENTATION_ID / ID_SCALE);
                break;
            case DRUM_MESSAGE_ID://last_cable_out, last_cable_speed
                type = "Drum Message";
                float cableDeployed = canIn.get_halffloat(0);
                float drumSpeed = canIn.get_halffloat(2);
                float radius = canIn.get_halffloat(4);
                status = canIn.get_short(6);
                message = "Status: " + Short.toString(status) + ", " + "Cable Deployed"
                        + Float.toString(drumSpeed) + ", " + "Working Radius: "
                        + Float.toString(radius)  + ", "
                        + "Message Value: " + Integer.toString(DRUM_MESSAGE_ID / ID_SCALE);
                break;
            case TENSION_MESSAGE_ID://status, activeDrum, lastCableTension
                type = "Tension Message";
                float tension = (float) canIn.get_byte(0);
                status = canIn.get_byte(3);
                message = "Status: " + Short.toString(status) + ", " + "Tension: "
                        + Float.toString(tension) + ", "
                        + "Message Value: " + Integer.toString(TENSION_MESSAGE_ID / ID_SCALE);
                break;
            case CABLE_ANGLE_MESSAGE_ID://status, activeDrum, lastCableTension
                type = "Cable Angle Message";
                float cableAngle = canIn.get_halffloat(0);
                status = canIn.get_short(2);
                message = "Status: " + Short.toString(status) + ", " + "Cable Angle"
                        + Float.toString(cableAngle) + ", "
                        + "Message Value: " + Integer.toString(CABLE_ANGLE_MESSAGE_ID / ID_SCALE);
                break;
            case DENSITY_ALTITUDE_ID://System.out.println("Density Altitude: " + pressure + " " +  temperature + " " + hummidity);
                type = "Density Altitude Message";
                float pressure = canIn.get_halffloat(0);
                float temperature = (float) canIn.get_byte(2);
                float humidity = (float) canIn.get_byte(3);
                status = canIn.get_byte(4);
                message = "Status: " + Short.toString(status) + ", " + "Air Pressure" 
                        + Float.toString(pressure) + ", " + "Temperature: " + 
                        Float.toString(temperature) + ", " + "Humidity: " + 
                        Float.toString(humidity) + ", "
                        + "Message Value: " + Integer.toString(DENSITY_ALTITUDE_ID / ID_SCALE);
                break;
            case WIND_ID://System.out.println("Wind: " + direction + " " +  speed + " " + gust);
                type = "Wind Message";
                float direction = (float) canIn.get_byte(0);
                float speed = canIn.get_halffloat(2);                    
                float gust = (float) canIn.get_byte(4);
                status = canIn.get_byte(6);
                message = "Status: " + Short.toString(status) + ", " + "Direction: "
                        + Float.toString(direction) + ", " + "Speed: " + 
                        Float.toString(speed) + ", " + "Gust: " + Float.toString(gust)
                         + ", " + "Message Value: " + Integer.toString(WIND_ID / ID_SCALE);
                break;
            case BATTERY_SYSTEM_ID://System.out.println("Battery System: " + voltage + " " +  current + " " + temperature);
                type = "Battery Message";
                float voltage = canIn.get_halffloat(0);
                float current = canIn.get_halffloat(2);
                float temp = (float) canIn.get_byte(4);
                status = canIn.get_byte(5);
                message = "Status: " + Short.toString(status) + ", " + "Voltage: "
                        + Float.toString(voltage) + ", " + "Current: " + Float.toString(current) 
                        + ", " + "Temperature: " + Float.toString(temp) + ", "
                        + "Message Value: " + Integer.toString(BATTERY_SYSTEM_ID / ID_SCALE);
                break;
            default:
                message = "Non-CanID Message"  + ", " + 
                        Integer.toString(canIn.id / ID_SCALE);
                break;
        }
        return type + ", " + message;
    }
}
