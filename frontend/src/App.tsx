import { BrowserRouter, Routes, Route } from "react-router-dom";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { JobsPage } from "./pages/JobsPage";
import { JobDetailPage } from "./pages/JobDetailPage";
import { PasteJobPage } from "./pages/PasteJobPage";
import { ProfilePage } from "./pages/ProfilePage";
import { TailorPage } from "./pages/TailorPage";
import { ResumesPage } from "./pages/ResumesPage";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/jobs" element={<JobsPage />} />
        <Route path="/jobs/paste" element={<PasteJobPage />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/jobs/:id" element={<JobDetailPage />} />
        <Route path="/jobs/:id/tailor" element={<TailorPage />} />
        <Route path="/resumes" element={<ResumesPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
