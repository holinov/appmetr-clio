package com.pixonic.pixapi.clio;

import java.io.IOException;

public class Program {
    public static void main(String[] args) {
        ClioNode node = new ClioNode();
        node.run();

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

        node.stop();
    }
}




