import { Route, BrowserRouter as Router, Routes } from 'react-router-dom';
import GithubAuthCallback from '../../pages/Auth/GithubCallback/GithubAuthCallback';
import SignIn from '../../pages/Auth/SignIn/SignIn';
import SignUp from '../../pages/Auth/SignUp/SignUp';
import Dashboard from '../../pages/Dashboard/Dashboard';
import Features from '../../pages/Home/Features';
import Footer from '../../pages/Home/footer';
import Header from '../../pages/Home/header';
import HeroSection from '../../pages/Home/HeroSection';
import Pricing from '../../pages/Home/Pricing';
import Testimonials from '../../pages/Home/Testimonials';
import RecordingData from '../../pages/RecordingData/RecordingData';
import RecordingList from '../../pages/RecordingList/RecordingList';
import Uploading from '../../pages/Upload/Uploading';
import UserProfile from '../../pages/UserProfile/UserProfile';
import UserProfileEdit from '../../pages/UserProfileEdit/UserProfileEdit';


function App() {
  return (
    <Router>
      <div className="min-h-screen flex flex-col">
        <Routes>
          <Route path="/*" element={
            <>
              <Header />
              <Routes>
                <Route path="/" element={
                  <main className="flex-grow">
                    <title>AudioScholar - Home</title>
                    <HeroSection />
                    <Features />
                    <Pricing />
                    <Testimonials />
                  </main>
                } />

                <Route path="/signup" element={<SignUp />} />

                <Route path="/signin" element={<SignIn />} />
              </Routes>
              <Footer />
            </>
          } />


          <Route path="/dashboard" element={<Dashboard />} />

          <Route path="/upload" element={<Uploading />} />

          <Route path="/recordings" element={<RecordingList />} />

          <Route path="/recordings/:id" element={<RecordingData />} />

          <Route path="/profile" element={<UserProfile />} />

          <Route path="/profile/edit" element={<UserProfileEdit />} />

          <Route path="/auth/github/callback" element={<GithubAuthCallback />} />


        </Routes>
      </div>
    </Router>
  );
}

export default App;