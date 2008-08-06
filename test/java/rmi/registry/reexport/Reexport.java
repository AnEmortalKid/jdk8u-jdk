/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 4120329
 * @summary RMI registry creation is impossible if first attempt fails.
 * @library ../../testlibrary
 * @build StreamPipe TestParams TestLibrary JavaVM
 * @build RegistryRunner RegistryRunner_Stub
 * @build Reexport
 * @run main/othervm Reexport
 */

/*
 * If a VM could not create an RMI registry because another registry
 * usually in another process, was using the registry port, the next
 * time the VM tried to create a registry (after the other registry
 * was brought down) the attempt would fail.  The second try to create
 * a registry would fail because the registry ObjID would still be in
 * use when it should never have been allocated.
 *
 * The test creates this conflict using Runtime.exec and ensures that
 * a registry can still be created after the conflict is resolved.
 */

import java.io.*;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.*;

public class Reexport {
    static public final int regport = TestLibrary.REGISTRY_PORT;

    static public void main(String[] argv) {

        Registry reg = null;

        try {
            System.err.println("\nregression test for 4120329\n");

            // establish the registry (we hope)
            System.err.println("Starting registry on port " + regport);
            Reexport.makeRegistry(regport);

            // Get a handle to the registry
            System.err.println("Creating duplicate registry, this should fail...");
            reg = createReg(true);

            if (reg != null) {
                TestLibrary.bomb("failed was able to duplicate the registry?!?");
            }

            // Kill the first registry.
            System.err.println("Bringing down the first registry");
            try {
                Reexport.killRegistry();
            } catch (Exception foo) {
            }

            // start another registry now that the first is gone; this should work
            System.err.println("Trying again to start our own " +
                               "registry... this should work");

            reg = createReg(false);

            if (reg == null) {
                TestLibrary.bomb("Could not create registry on second try");
            }

            System.err.println("Test passed");

        } catch (Exception e) {
            TestLibrary.bomb(e);
        } finally {
            // dont leave the registry around to affect other tests.
            killRegistry();

            reg = null;
        }
    }

    static Registry createReg(boolean remoteOk) {
        Registry reg = null;

        try {
            reg = LocateRegistry.createRegistry(regport);
        } catch (Throwable e) {
            if (remoteOk) {
                System.err.println("EXPECTING PORT IN USE EXCEPTION:");
                System.err.println(e.getMessage());
                e.printStackTrace();
            } else {
                TestLibrary.bomb((Exception) e);
            }
        }

        return reg;
    }

    public static void makeRegistry(int p) {
        // sadly, we can't kill a registry if we have too-close control
        // over it.  We must make it in a subprocess, and then kill the
        // subprocess when it has served our needs.

        try {
            JavaVM jvm = new JavaVM("RegistryRunner", "", Integer.toString(p));
            jvm.start();
            Reexport.subreg = jvm.getVM();

        } catch (IOException e) {
            // one of these is summarily dropped, can't remember which one
            System.out.println ("Test setup failed - cannot run rmiregistry");
            TestLibrary.bomb("Test setup failed - cannot run test", e);
        }
        // Slop - wait for registry to come up.  This is stupid.
        try {
            Thread.sleep (5000);
        } catch (Exception whatever) {
        }
    }
    private static Process subreg = null;

    public static void killRegistry() {
        if (Reexport.subreg != null) {

            RegistryRunner.requestExit();

            try {
                Reexport.subreg.waitFor();
            } catch (InterruptedException ie) {
            }
        }
        Reexport.subreg = null;
    }
}
