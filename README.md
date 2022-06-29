### Windows release of the single-player 2009scape experience

#### How to use this release
1. Find the big blue button that says "Clone".
2. Click the one to its left.
3. Select ZIP release.
4. Download & extract the ZIP to a directory of your liking - preferably desktop or something like that. Program Files likely won't work.
5. Execute `launch.bat`. Enjoy the game.

Notes:
- First launch will take a bit longer because the database will have to be properly configured.
- If you change internal server configuration and things suddenly start to be wonky - that's on you. The release is meant to be enjoyed as-is and any modifications to the server configuration files are your own.

#### My internet is very slow and the gitlab download does not work
**Note: This link is not guaranteed to be updated to the latest singleplayer, it is included as a courtesy.**

Here's an LZMA-compressed copy of singleplayer as it was on 23/6/2022:

https://mega.nz/file/nIpHDAbY#zLH1SyzNTU5y0elAJqU9rEGL1nVoAoFLxpyACod8QFI

You should be able to extract this with something like 7zip on Windows. (It'll probably require two steps - tar.xz decompresses into .tar, then you extract the tar, both steps with 7z.)

#### Troubleshooting

##### My character's stats are decreasing after each login**  
You changed your profile's XP rate to way higher than what is supported. Maximum supported XP rate is `5.0` for normal players and `10.0` for Ultimate Ironman players. There are no plans to change the maximum supported XP rates in the future. *Requests to change it will be ignored.*

##### The console log keeps repeating `Still waiting for the server to start...`**  
There are two known things that can cause this:
 - You have extracted the files to a path containing spaces, e.g.: `C:\Users\My Name\Documents\2009scape`. Move the directory someplace else where there's no spaces in the path.
 - The MySQL server has failed to start. Make sure there is no `mysqld.exe` in the Task Manager and that there are no other database systems running in the background.
 
##### The MySQL server says `bin\mysqld.exe: Can't change dir to '<path>' (Errcode: 2  "No such file or directory")`**  
You have moved the game directory after launching the game for the first time. To fix this:
1. Close the console log to shut down the server software.
2. Locate `db/data` directory.
3. Edit `my.ini` file located therein:
    1. Locate line `datadir=<path>`, where `<path>` is the path displayed by the error message.
    2. Change the part before `/db/data` to the path you have moved the game directory to.
    3. Save the file, close your editor.
    4. Restart the server.
4. The server should now start properly. If it doesn't, verify the path you entered is correct.
