import javafx.util.Pair;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;

public class Main {
    public static String START_FILE = "FFD8";
    public static String FFE0 = "FFE0";
    public static String FFE1 = "FFE1";
    public long linkToAlign;
    public long startOfStructure;

    public static void main(String[] args) throws IOException {
        new Main().run();
    }

    private void run() throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter container path");
//        String path = "IMG_0001.jpg";
        String path = scanner.nextLine();
        System.out.println("Enter operation:\n1 - write information to container\n2 - get information from container");
//        String operation = "2";
        String operation = scanner.nextLine();
//        String message = "testMessage.txt";
        String message = "";
        byte[] messageBytes = new byte[0];
        if (operation.equals("1")) {
            System.out.println("Enter message path");
            message = scanner.nextLine();
            Path messagePath = Paths.get(message);
            messageBytes = Files.readAllBytes(messagePath);
        }


//        Path messagePath = Paths.get(message);
//        byte[] messageBytes = Files.readAllBytes(messagePath);

        RandomAccessFile file = new RandomAccessFile(path, "rw");
        workWithJpegFile(file, messageBytes, operation);

    }

    private void workWithJpegFile(RandomAccessFile file, byte[] message, String operation) throws IOException {
        file.seek(0);
        byte[] bytes = new byte[2];
        file.read(bytes, 0, 2);
        if (!bytesToHex(bytes).equals(START_FILE)) {
            System.out.println("Not found JPEG file!");
            return;
        }
        long linkToIDMarker = file.getFilePointer();
        file.read(bytes, 0, 2);
        String hex = bytesToHex(bytes);
        if (hex.equals(FFE0)) {
            readFFE0(file);
        } else {
            file.seek(linkToIDMarker);
        }
        file.read(bytes, 0, 2);
        hex = bytesToHex(bytes);
        if (hex.equals(FFE1)) {
            startOfStructure = file.getFilePointer();
            if (operation.equals("1")) {
                long availableSize = readFFE1(file);
                if (message.length + 2 <= availableSize) {
                    writeFFE1(file, message);
                    System.out.println("Information are written!");
                } else {
                    System.out.println("Invalid data : message more than available container \nsize of message: " + (message.length + 2) +
                            "\navailable size in container: " + availableSize);
//                    return;
                }
            } else {
                byte[] resultMessage = readInformation(file);
                if (resultMessage == null){
                    System.out.println("Container has not information!");
                    return;
                }
                try (FileOutputStream fos = new FileOutputStream("resultMessage")) {
                    fos.write(resultMessage);
                }
                System.out.println("Information are got from container and written to resultMessage!");
            }
            /*if (linkToThumbnail == 0) {
                System.out.println("File doesn't contain thumbnail sector!");
            } else {
                Pair<Long, Long> links = getLinks(file, linkToThumbnail, message);
                if (Objects.equals(operation, "1")) {
                    writeInformation(file, links, message);
                    System.out.println("Information are written!");
                } else {
                    byte[] tmpMessage = readInformation(file, links);
                    try (FileOutputStream fos = new FileOutputStream("resultMessage")){
                        fos.write(tmpMessage);
                    }
                }
            }*/
        }
    }

    private byte[] readInformation(RandomAccessFile file) throws IOException {
        file.seek(startOfStructure);
        long sizeOfMessage;
        byte[] resultMessage = new byte[0];
        int messagePointer = 0;
        byte[] bytes = new byte[2];
        file.read(bytes, 0, 2);
        int length = Integer.parseInt(bytesToHex(bytes), 16);
        //System.out.println(length);
        byte[] byteEXIF = new byte[6];
        file.read(byteEXIF, 0, 6);
        linkToAlign = file.getFilePointer();
        //System.out.println(linkToAlign);
        file.read(bytes, 0, 2);
        file.read(bytes, 0, 2);
        byte[] byteOffsetFirstIFD = new byte[4];
        file.read(byteOffsetFirstIFD, 0, 4);
        reverseArray(byteOffsetFirstIFD);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(bytesToHex(byteOffsetFirstIFD));

        int offsetFirstIFD = Integer.parseInt(bytesToHex(byteOffsetFirstIFD), 16);
        file.seek(linkToAlign + offsetFirstIFD);
        file.read(bytes, 0, 2);
        reverseArray(bytes);
        int nDirEntry = Integer.parseInt(bytesToHex(bytes), 16);
        for (int i = 0; i < nDirEntry; i++) {
            byte[] name = new byte[2];
            file.read(name, 0, 2);
            reverseArray(name);
//            System.out.println(bytesToHex(name));

            byte[] type = new byte[2];
            file.read(type, 0, 2);
            reverseArray(type);
//            System.out.println(bytesToHex(type));


            byte[] numberOfComponents = new byte[4];
            file.read(numberOfComponents, 0, 4);
            reverseArray(numberOfComponents);
//            System.out.println(bytesToHex(numberOfComponents));

            byte[] value = new byte[2];
            long pointer = file.getFilePointer();
            file.read(value, 0, 2);
            reverseArray(value);
            byte[] trash = new byte[2];
            file.read(trash, 0, 2);
            reverseArray(trash);
            long endPointer = file.getFilePointer();
            if (Objects.equals(bytesToHex(name), "0100")) {
//                reverseArray(value);
                sizeOfMessage = Long.parseLong(bytesToHex(value), 16);
                resultMessage = new byte[(int) sizeOfMessage];
                if (trash[1] != 3){
                    return null;
                }
//                file.seek(pointer);
//                for (int j = 4; j >= 0; j -= 2) {
//                    file.write(Integer.parseInt(sizeInHex.substring(j, j + 2), 16));
//                }
                file.seek(endPointer);
            } else if (!Objects.equals(bytesToHex(name), "8769")) {
                if (Integer.parseInt(bytesToHex(numberOfComponents), 16) == 1) {
                    //
                    file.seek(pointer);
                    byte[] buff = new byte[4];
                    file.read(buff, 0, 4);
                    for (int j = 0; j < buff.length; j++) {
                        if (messagePointer < resultMessage.length) {
                            resultMessage[messagePointer++] = buff[j];
                        }
                    }
                    file.seek(endPointer);
                }
            }
        }
        return resultMessage;
    }

    private void writeFFE1(RandomAccessFile file, byte[] message) throws IOException {
        file.seek(startOfStructure);
        long sizeOfMessage = message.length;
        String sizeInHex = Long.toHexString(sizeOfMessage);
        while (sizeInHex.length() < 4) {
            sizeInHex = "0" + sizeInHex;
        }
        int messagePointer = 0;
        byte[] bytes = new byte[2];
        file.read(bytes, 0, 2);
        int length = Integer.parseInt(bytesToHex(bytes), 16);
        //System.out.println(length);
        byte[] byteEXIF = new byte[6];
        file.read(byteEXIF, 0, 6);
        linkToAlign = file.getFilePointer();
        //System.out.println(linkToAlign);
        file.read(bytes, 0, 2);
        file.read(bytes, 0, 2);
        byte[] byteOffsetFirstIFD = new byte[4];
        file.read(byteOffsetFirstIFD, 0, 4);
        reverseArray(byteOffsetFirstIFD);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(bytesToHex(byteOffsetFirstIFD));

        int offsetFirstIFD = Integer.parseInt(bytesToHex(byteOffsetFirstIFD), 16);
        file.seek(linkToAlign + offsetFirstIFD);
        file.read(bytes, 0, 2);
        reverseArray(bytes);
        int nDirEntry = Integer.parseInt(bytesToHex(bytes), 16);
        for (int i = 0; i < nDirEntry; i++) {
            byte[] name = new byte[2];
            file.read(name, 0, 2);
            reverseArray(name);
//            System.out.println(bytesToHex(name));

            byte[] type = new byte[2];
            file.read(type, 0, 2);
            reverseArray(type);
//            System.out.println(bytesToHex(type));


            byte[] numberOfComponents = new byte[4];
            file.read(numberOfComponents, 0, 4);
            reverseArray(numberOfComponents);
//            System.out.println(bytesToHex(numberOfComponents));

            byte[] value = new byte[2];
            long pointer = file.getFilePointer();
            file.read(value, 0, 2);
            reverseArray(value);
            byte[] trash = new byte[2];
            file.read(trash, 0, 2);
            reverseArray(trash);
            long endPointer = file.getFilePointer();
            if (Objects.equals(bytesToHex(name), "0100")) {
                file.seek(pointer);
                for (int j = 2; j >= 0; j -= 2) {
                    file.write(Integer.parseInt(sizeInHex.substring(j, j + 2), 16));
                }
                file.write(3);
                file.seek(endPointer);
            } else if (!Objects.equals(bytesToHex(name), "8769")) {
                if (Integer.parseInt(bytesToHex(numberOfComponents), 16) == 1) {
                    //
                    file.seek(pointer);
                    for (int j = 0; j < 4; j++) {
                        if (messagePointer < message.length) {
                            file.write(message[messagePointer++]);
                        }
                    }
                    file.seek(endPointer);
                }
            }

        }
        byte[] byteNextIFDOffset = new byte[4];
        file.read(byteNextIFDOffset, 0, 4);
        reverseArray(byteNextIFDOffset);
    }

    private long readFFE1(RandomAccessFile file) throws IOException {
        long result = 0;
        file.seek(startOfStructure);
        byte[] bytes = new byte[2];
        file.read(bytes, 0, 2);
        int length = Integer.parseInt(bytesToHex(bytes), 16);
        //System.out.println(length);
        byte[] byteEXIF = new byte[6];
        file.read(byteEXIF, 0, 6);
        linkToAlign = file.getFilePointer();
        //System.out.println(linkToAlign);
        file.read(bytes, 0, 2);
        file.read(bytes, 0, 2);
        byte[] byteOffsetFirstIFD = new byte[4];
        file.read(byteOffsetFirstIFD, 0, 4);
        reverseArray(byteOffsetFirstIFD);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(bytesToHex(byteOffsetFirstIFD));

        int offsetFirstIFD = Integer.parseInt(bytesToHex(byteOffsetFirstIFD), 16);
        file.seek(linkToAlign + offsetFirstIFD);
        file.read(bytes, 0, 2);
        reverseArray(bytes);
        int nDirEntry = Integer.parseInt(bytesToHex(bytes), 16);
        for (int i = 0; i < nDirEntry; i++) {
            byte[] name = new byte[2];
            file.read(name, 0, 2);
            reverseArray(name);
            //System.out.println(bytesToHex(name));

            byte[] type = new byte[2];
            file.read(type, 0, 2);
            reverseArray(type);
            //System.out.println(bytesToHex(type));


            byte[] numberOfComponents = new byte[4];
            file.read(numberOfComponents, 0, 4);
            reverseArray(numberOfComponents);
            //System.out.println(bytesToHex(numberOfComponents));

            byte[] value = new byte[2];
            long pointer = file.getFilePointer();
            file.read(value, 0, 2);
            reverseArray(value);
            byte[] trash = new byte[2];
            file.read(trash, 0, 2);
            reverseArray(trash);
            long endPointer = file.getFilePointer();
            if (!Objects.equals(bytesToHex(name), "8769")) {
                if (Integer.parseInt(bytesToHex(numberOfComponents), 16) == 1) {
                    result += 4;
//                    file.seek(pointer);
//                    file.write("le".getBytes());
                    file.seek(endPointer);
                }
            }

        }
        return result;
    }


    private void reverseArray(byte[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            byte temp = array[i];
            array[i] = array[array.length - i - 1];
            array[array.length - i - 1] = temp;
        }
    }

    private long readFFE0(RandomAccessFile file) throws IOException {
        long startOfStructure = file.getFilePointer();
        byte[] bytes = new byte[2];
        file.read(bytes, 0, 2);
        int length = Integer.parseInt(bytesToHex(bytes), 16);
        //System.out.println(length);
        file.seek(startOfStructure);
        byte[] structure = new byte[length];
        file.read(structure, 0, length);
        //System.out.println(bytesToHex(structure));
        return 0;
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
