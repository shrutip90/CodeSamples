StatApp
=======
 
Android application to collect file stats. The source code is from the Android Studio project.

It will list the whole directory structures of Android systems, and get the stat info for each file that is accessible by a user. If the file is not accessible, then it just skips it.

The log file will be saved in the `/sdcard/statLog.txt` and can be trasnferred over a socket connection using 'Send Data' button that appears after data collection.

Collected Data: The data we will collect will be like this.

    660,-rw-rw----,8,512,18,81b0,regular file,1028,UNKNOWN,1,3093764104,?,/sdcard/BeyondPod/BeyondPodHistory.bin.autobak,`/sdcard/BeyondPod/BeyondPodHistory.bin.autobak',4096,396,0,0,0,UNKNOWN,?,?,2014-06-20 15:02:45.852978727 +0000,1403276565,2014-09-27 15:44:16.343191746 +0000,1411832656,2014-09-27 15:44:16.343191746 +0000,1411832656
    770,drwxrwx---,8,512,18,41f8,directory,1028,UNKNOWN,2,3093975512,?,/sdcard/DCIM/100ANDRO,`/sdcard/DCIM/100ANDRO',4096,4096,0,0,0,UNKNOWN,?,?,2014-05-13 23:39:14.737654351 +0000,1400024354,2014-05-13 23:39:14.737654351 +0000,1400024354,2014-05-13 23:39:14.737654351 +0000,1400024354

## stat 
    stat -c '%a,%A,%b,%B,%d,%f,%F,%g,%G,%h,%i,%m,%n,%N,%o,%s,%t,%T,%u,%U,%w,%W,%x,%X,%y,%Y,%z,%Z'

### Reference for each row
    00. %a   access rights in octal
    01. %A   access rights in human readable form
    02. %b   number of blocks allocated (see %B)
    03. %B   the size in bytes of each block reported by %b
    04. %d   device number in decimal
    05. %f   raw mode in hex
    06. %F   file type
    07. %g   group ID of owner
    08. %G   group name of owner
    09. %h   number of hard links
    10. %i   inode number
    11. %m   mount point
    12. %n   file name
    13. %N   quoted file name with dereference if symbolic link
    14. %o   optimal I/O transfer size hint
    15. %s   total size, in bytes
    16. %t   major device type in hex
    17. %T   minor device type in hex
    18. %u   user ID of owner
    19. %U   user name of owner
    20. %w   time of file birth, human-readable; - if unknown
    21. %W   time of file birth, seconds since Epoch; 0 if unknown
    22. %x   time of last access, human-readable
    23. %X   time of last access, seconds since Epoch
    24. %y   time of last modification, human-readable
    25. %Y   time of last modification, seconds since Epoch
    26. %z   time of last change, human-readable
    27. %Z   time of last change, seconds since Epoch

## To install using APK file:
    1. Allow installation of apps from unknown sources under Settings -> Security -> Device Administration -> Unknown Sources
    2. Transfer the APK file to the phone storage through USB.
    3. Install "Easy Installer" from Google Play.
    4. Open "Easy Installer" and scan for apps.
    5. 'StatApp' will appear in the list of apps that can be installed.
    6. Select it and install.
