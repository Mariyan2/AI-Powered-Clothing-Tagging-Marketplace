# AI-Powered Clothing Tagging Marketplace

This project demonstrates how modern AI vision models can enhance product discovery and categorization for fashion marketplaces.  
Traditional alt-text tools often miss style cues such as *streetwear*, *alternative*, *gothic*, or *minimalist*.  
This application compares:

- **LLM-generated tags & titles**  
- **Traditional alt-text tags**

To showcase the Languge models usefulness in more accurate recommendations.

---
<img width="1450" height="1216" alt="image" src="https://github.com/user-attachments/assets/446b34c2-b9d0-462f-a1f8-9be76ce2c17f" />



**The search can be entered manually or by clicking on one of the tags.**
---
<img width="1430" height="594" alt="image" src="https://github.com/user-attachments/assets/ea35728c-bdb0-433c-8868-b6f038fd8cf1" />

---

# How to Run the Project

This project includes:
- Backend: Spring Boot (port 8080)
- Frontend: React + Vite (port 5173)

---

## Requirements
- Java 17+
- Node.js 18+
- No global Maven installation required (the project uses the Maven Wrapper)

---

## Environment Files (Required)

Place the following files inside this directory:

backend/src/main/resources/

Required files:
- backend/src/main/resources/application.properties
- backend/src/main/resources/firebase/service-account.json

These are required for Firebase Storage and AI API configuration.

---

## Start the Backend (Spring Boot)

### Windows PowerShell

```powershell
cd backend
./mvnw.cmd spring-boot:run
```

### macOS / Linux
```
cd backend
./mvnw spring-boot:run
```
Backend will run at:
http://localhost:8080

---

## Start the Frontend (React + Vite)

### Windows PowerShell
cd frontend/ai-tagging-frontend
npm install
npm run dev

Frontend will run at:
http://localhost:5173

---

## Notes
- Ensure backend CORS is configured to allow the frontend origin (http://localhost:5173).

---






## 🔧 Technologies Used

### **Frontend**
- React + Vite  
- Tailwind CSS  

### **Backend**
- Spring Boot 
- Firebase Storage (image uploads)
- Firebase Firestore (database)
- OpenAI / Azure computer vision API for image tagging
- Apache Lucene (Search Library)

Frontend runs on:
http://localhost:5173

