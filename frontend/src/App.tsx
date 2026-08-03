import { BrowserRouter, Routes, Route } from "react-router-dom";
import { HomePage } from "./pages/HomePage";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { JobsPage } from "./pages/JobsPage";
import { JobDetailPage } from "./pages/JobDetailPage";
import { PasteJobPage } from "./pages/PasteJobPage";
import { ProfilePage } from "./pages/ProfilePage";
import { SettingsPage } from "./pages/SettingsPage";
import { TailorPage } from "./pages/TailorPage";
import { ResumesPage } from "./pages/ResumesPage";
import { ApplicationsPage } from "./pages/ApplicationsPage";
import { ApplicationDetailPage } from "./pages/ApplicationDetailPage";
import { CompanyIntelPage } from "./pages/CompanyIntelPage";
import { CompanyIntelDetailPage } from "./pages/CompanyIntelDetailPage";
import { TrainerPage } from "./pages/TrainerPage";
import { ReviewQueuePage } from "./pages/ReviewQueuePage";
import { NotFoundPage } from "./pages/NotFoundPage";

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
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/jobs/:id" element={<JobDetailPage />} />
        <Route path="/jobs/:id/tailor" element={<TailorPage />} />
        <Route path="/resumes" element={<ResumesPage />} />
        <Route path="/applications" element={<ApplicationsPage />} />
        <Route path="/applications/:id" element={<ApplicationDetailPage />} />
        <Route path="/intel" element={<CompanyIntelPage />} />
        <Route path="/intel/:companyName" element={<CompanyIntelDetailPage />} />
        <Route path="/trainer" element={<TrainerPage />} />
        <Route path="/trainer/review" element={<ReviewQueuePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
