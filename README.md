# NeonPulse 🎵

NeonPulse is a feature-rich, desktop music player application built with Java and JavaFX. It offers a sleek user interface for managing local music libraries, creating playlists, and tracking listening history, all backed by a local SQLite database.

## ✨ Features

* **User Authentication:** Secure login and signup functionality to keep user profiles and libraries separate.
* **Music Library Management:** Import and play local audio files seamlessly.
* **Playlist Creation:** Create, edit, and manage custom playlists.
* **Smart Tracking:** Automatically tracks "Recently Played" and "Liked" songs.
* **Queue Management:** View and control your upcoming song queue.
* **Modern UI:** A custom-styled JavaFX interface with dedicated views for all songs, playlists, user profile, and a persistent player bar.

## 🛠️ Tech Stack

* **Language:** Java 17
* **GUI Framework:** JavaFX
* **Database:** SQLite (with JDBC)
* **Build Tool:** Maven
* **Security:** jBCrypt (for password hashing)

## 🚀 Getting Started

### Prerequisites

Make sure you have the following installed on your machine:
* [Java Development Kit (JDK) 17](https://adoptium.net/) or higher
* [Apache Maven](https://maven.apache.org/)

### Installation & Execution

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/LAN-SHLOK/Neonpulse.git](https://github.com/LAN-SHLOK/Neonpulse.git)

2. **Navigate to the project directory:**
   ```bash
   cd Neonpulse

3. **Build the project using Maven:**
   ```bash
   mvn clean install

4. **Run the application:**
   ```bash
   mvn javafx:run

## 🧠 Development Journey & Challenges Overcome

Building and deploying NeonPulse involved navigating several technical hurdles across both application development and version control:

### Application Development
* **State Management in JavaFX:** Coordinating UI updates and data flow across multiple distinct controllers (such as the PlayerBar, Queue Panel, and Main views) while ensuring the interface remained responsive.
* **Audio Engine & Queueing:** Managing continuous audio playback, smooth transitions between tracks, and dynamic queue updates without blocking or interrupting the main JavaFX thread.
* **Database & Security Integration:** Designing a reliable local SQLite database architecture to link users, their specific playlists, and listening history, while securely hashing and verifying user passwords using jBCrypt.
* **Application Packaging:** Configuring Maven and dependencies to successfully package the JavaFX application, external libraries, and local database into a cohesive, runnable executable format. 

### Version Control & Deployment
* **Command-Line Syntax Navigation:** Overcame Bash syntax errors caused by shell redirection characters during remote repository linking.
* **Large File Management:** Handled the accidental staging of massive build files (over 400 MB of `target/`, `.jar`, and `.zip` files) by writing a strict, custom `.gitignore`.
* **Git History Rectification:** Resolved persistent corrupted Git history issues where Git continuously attempted to push oversized files from the initial commit.
* **Environment Conflicts:** Successfully executed a complete Git reset to clear out corrupted tracking data, navigating around IDE file-lock interruptions to create a clean, lightweight repository.

  
## 🚀 Future Enhancements

* **Advanced Audio Controls:** Implementation of a graphic equalizer and audio visualizer.
* **Global Search:** A unified search bar to quickly find songs, artists, or specific playlists within the local library.
* **Extended Metadata:** Fetching and displaying real-time lyrics or album art for imported tracks.

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! 
Feel free to check the [issues page](https://github.com/LAN-SHLOK/Neonpulse/issues) if you want to contribute or suggest a new feature.

## 📝 License

This project is open-source and available under the [MIT License](LICENSE).

## 👤 Author

**Shlok Patel** * GitHub: [@LAN-SHLOK](https://github.com/LAN-SHLOK)
