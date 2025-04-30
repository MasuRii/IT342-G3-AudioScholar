import { Route, BrowserRouter as Router, Routes } from 'react-router-dom';
import Dashboard from './components/dashboard';
import Features from './components/features';
import Footer from './components/footer';
import GithubAuthCallback from './components/GithubAuthCallback';
import Header from './components/header';
import HeroSection from './components/HeroSection';
import Pricing from './components/pricing';
import RecordingData from './components/RecordingData';
import RecordingList from './components/RecordingList';
import SignIn from './components/SignIn';
import SignUp from './components/SignUp';
import Testimonials from './components/testimonials';
import Uploading from './components/Uploading';
import UserProfile from './components/UserProfile';
import UserProfileEdit from './components/UserProfileEdit';


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