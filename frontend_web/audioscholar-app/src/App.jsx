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

function App() {
  return (
    <Router>
      <div className="min-h-screen flex flex-col">
        <Routes>
          {/* Routes that use the shared Header */}
          <Route path="/*" element={
            <>
              <Header />
              <Routes>
                {/* Home Page Route */}
                <Route path="/" element={
                  <main className="flex-grow">
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
              <Footer />
            </>
          } />

          {/* Upload route with its own header built-in */}
          <Route path="/dashboard" element={<Uploading />} />
        </Routes>
      </div>
    </Router>
  );
}

export default App;