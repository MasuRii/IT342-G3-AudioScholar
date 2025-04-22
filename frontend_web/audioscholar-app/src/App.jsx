// app.jsx
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Header from './components/header';
import HeroSection from './components/HeroSection';
import Features from './components/features';
import Pricing from './components/pricing';
import Testimonials from './components/testimonials';
import Footer from './components/footer';
import SignUp from './components/SignUp';
import SignIn from './components/SignIn';
import Uploading from './components/Uploading';
import Dashboard from './components/Dashboard';
import RecordingList from './components/RecordingList';
import UserProfile from './components/UserProfile';
import Summaryview from './components/Summaryview'; // Import the Summaryview component

function App() {
  return (
    <Router>
      <div className="min-h-screen flex flex-col">
        <Routes>
          {/* This route uses a wildcard (*) to match any path that hasn't been matched
            by the specific routes defined outside this block.
            It renders the Header and Footer for the specified nested routes.
            Keep this structure as it is for the routes that need the main Header/Footer.
          */}
          <Route path="/*" element={
            <>
              <Header /> {/* Main application header */}
              <Routes>
                {/* Home Page Route */}
                <Route path="/" element={
                  <main className="flex-grow"> {/* Use main tag for primary content */}
                    <HeroSection />
                    <Features />
                    <Pricing />
                    <Testimonials />
                  </main>
                } />

                {/* Sign Up Page Route */}
                <Route path="/signup" element={<SignUp />} />

                {/* Sign In Page Route */}
                            <Route path="/signin" element={<SignIn />} />
              </Routes>
              <Footer /> {/* Main application footer */}
            </>
          } />

          {/* Routes below this line do NOT automatically get the main Header and Footer
            rendered by the wildcard route above.
            You should place routes here that have their own specific layout
            (like the Summaryview with its unique header/layout).
          */}

          {/* Dashboard route */}
          {/* Add flex-grow to dashboard content if needed for layout consistency */}
          <Route path="/dashboard" element={<Dashboard />} />

          {/* Upload route */}
           {/* Add flex-grow to upload content if needed for layout consistency */}
          <Route path="/upload" element={<Uploading />} />

          {/* Recording List route */}
           {/* Add flex-grow to recording list content if needed for layout consistency */}
          <Route path="/recordings" element={<RecordingList />} />

          {/* User Profile route */}
           {/* Add flex-grow to user profile content if needed for layout consistency */}
          <Route path="/profile" element={<UserProfile />} />

          {/* Summary View route
            This route includes a URL parameter ':recordingId'
            to specify which recording's summary to load.
          */}
          <Route path="/summary/:recordingId" element={<Summaryview />} />

           {/*
             Optional: Add a catch-all for 404 Not Found pages here
             <Route path="*" element={<NotFoundPage />} />
           */}
        </Routes>
      </div>
    </Router>
  );
}

export default App;